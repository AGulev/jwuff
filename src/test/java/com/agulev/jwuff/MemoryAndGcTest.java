package com.agulev.jwuff;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.concurrent.TimeUnit;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryAndGcTest {
    @Test
    void allocationsAndGcBehaviorOnTestPng() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("jwuff.mem"),
                "Enable with -Djwuff.mem=true (large/slow and environment-dependent).");
        Assumptions.assumeTrue(isMacArm64(), "macOS arm64 only");

        byte[] png = readResourceBytes("/images/test.png");

        ImageIO.scanForPlugins();
        boolean prevUseCache = ImageIO.getUseCache();
        ImageIO.setUseCache(false);

        try {
            System.out.println("=== MemoryAndGcTest ===");
            System.out.printf(Locale.ROOT, "java=%s; maxHeapMiB=%d; ImageIO.useCache=%s%n",
                    System.getProperty("java.version"),
                    Runtime.getRuntime().maxMemory() / (1024 * 1024),
                    ImageIO.getUseCache()
            );
            System.out.printf(Locale.ROOT, "test.png bytes=%d%n", png.length);

            CycleResult standard = runCycle("standard", () -> readWithStandardImageIo(png));
            CycleResult jwuffImageIo = runCycle("jwuff(ImageIO.read)", () -> readWithJwuffImageIo(png));
            CycleResult jwuffDirect = runCycle("jwuff(direct)", () -> readWithJwuffDirect(png));

            printTable(standard, jwuffImageIo, jwuffDirect);

            for (CycleResult r : new CycleResult[]{jwuffImageIo, jwuffDirect}) {
                if (standard.allocatedBytes >= 0 && r.allocatedBytes >= 0) {
                    assertTrue(r.allocatedBytes <= standard.allocatedBytes * 1.10,
                            "Expected " + r.label + " allocated bytes not worse than standard by >10% (env-dependent): " +
                                    r.label + "=" + r.allocatedBytes + ", standard=" + standard.allocatedBytes);
                }
            }
            for (CycleResult r : new CycleResult[]{standard, jwuffImageIo, jwuffDirect}) {
                assertTrue(r.afterDropGcUsedBytes < 512L * 1024 * 1024,
                        "Expected " + r.label + " heap used after drop+GC < 512 MiB, got: " + fmtBytes(r.afterDropGcUsedBytes));
            }
        } finally {
            ImageIO.setUseCache(prevUseCache);
        }
    }

    private static BufferedImage readWithJwuffImageIo(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static BufferedImage readWithStandardImageIo(byte[] bytes) {
        try {
            return PerformanceComparisonTest.withWuffsProvidersDisabledForTest(() -> ImageIO.read(new ByteArrayInputStream(bytes)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static BufferedImage readWithJwuffDirect(byte[] bytes) {
        try {
            ImageReaderSpi spi = new com.agulev.jwuff.spi.WuffsPngImageReaderSpi();
            ImageReader reader = spi.createReaderInstance();
            try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                assertNotNull(iis);
                reader.setInput(iis, false, true);
                BufferedImage img = reader.read(0);
                assertNotNull(img);
                return img;
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] readResourceBytes(String path) throws Exception {
        try (InputStream in = MemoryAndGcTest.class.getResourceAsStream(path)) {
            Assumptions.assumeTrue(in != null, "Missing resource: " + path);
            return in.readAllBytes();
        }
    }

    private static boolean isMacArm64() {
        String osName = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(java.util.Locale.ROOT);
        return osName.contains("mac") && (arch.equals("aarch64") || arch.equals("arm64"));
    }

    private static CycleResult runCycle(String label, ThrowingSupplier<BufferedImage> read) throws Exception {
        forceGc(2, 50);
        long beforeUsed = heapUsage().getUsed();

        final BufferedImage[] holder = new BufferedImage[1];
        long t0 = System.nanoTime();
        long allocated = ThreadAllocations.measureAllocatedBytesForCurrentThread(() -> {
            try {
                holder[0] = read.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        long t1 = System.nanoTime();
        assertNotNull(holder[0], label + " read returned null");
        holder[0].getRGB(0, 0);

        long afterReadUsed = heapUsage().getUsed();

        holder[0] = null;
        forceGc(6, 100);
        long afterDropGcUsed = heapUsage().getUsed();

        return new CycleResult(label, beforeUsed, afterReadUsed, afterDropGcUsed, allocated, TimeUnit.NANOSECONDS.toMillis(t1 - t0));
    }

    private static void forceGc(int rounds, long sleepMillis) throws InterruptedException {
        for (int i = 0; i < rounds; i++) {
            System.gc();
            Thread.sleep(sleepMillis);
        }
    }

    private static MemoryUsage heapUsage() {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
    }

    private static String fmtBytes(long bytes) {
        double mib = bytes / (1024.0 * 1024.0);
        return String.format(java.util.Locale.ROOT, "%.1f MiB", mib);
    }

    private static void printTable(CycleResult... results) {
        String[] steps = new String[]{
                "gc + measure(before)",
                "read + measure(after)",
                "drop + gc + measure(after)",
                "allocated bytes (ThreadMXBean)",
                "read time",
        };

        String[][] cell = new String[steps.length][results.length + 1];
        for (int r = 0; r < steps.length; r++) {
            cell[r][0] = steps[r];
        }
        for (int c = 0; c < results.length; c++) {
            CycleResult res = results[c];
            cell[0][c + 1] = fmtBytes(res.beforeUsedBytes);
            cell[1][c + 1] = fmtBytes(res.afterReadUsedBytes);
            cell[2][c + 1] = fmtBytes(res.afterDropGcUsedBytes);
            cell[3][c + 1] = res.allocatedBytes >= 0 ? fmtBytes(res.allocatedBytes) : "n/a";
            cell[4][c + 1] = res.readMillis + " ms";
        }

        int[] widths = new int[results.length + 1];
        widths[0] = "step".length();
        for (String s : steps) widths[0] = Math.max(widths[0], s.length());
        for (int c = 0; c < results.length; c++) {
            widths[c + 1] = results[c].label.length();
            for (int r = 0; r < steps.length; r++) {
                widths[c + 1] = Math.max(widths[c + 1], cell[r][c + 1].length());
            }
        }

        System.out.println();
        System.out.printf(Locale.ROOT, "%-" + widths[0] + "s", "step");
        for (int c = 0; c < results.length; c++) {
            System.out.printf(Locale.ROOT, " | %-" + widths[c + 1] + "s", results[c].label);
        }
        System.out.println();

        System.out.print("-".repeat(widths[0]));
        for (int c = 0; c < results.length; c++) {
            System.out.print("-+-");
            System.out.print("-".repeat(widths[c + 1]));
        }
        System.out.println();

        for (int r = 0; r < steps.length; r++) {
            System.out.printf(Locale.ROOT, "%-" + widths[0] + "s", cell[r][0]);
            for (int c = 0; c < results.length; c++) {
                System.out.printf(Locale.ROOT, " | %-" + widths[c + 1] + "s", cell[r][c + 1]);
            }
            System.out.println();
        }
        System.out.println();
    }

    private record CycleResult(
            String label,
            long beforeUsedBytes,
            long afterReadUsedBytes,
            long afterDropGcUsedBytes,
            long allocatedBytes,
            long readMillis
    ) {}

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
