package com.agulev.jwuff;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
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

        System.out.println("=== MemoryAndGcTest ===");
        System.out.printf(Locale.ROOT, "java=%s; maxHeapMiB=%d%n", System.getProperty("java.version"), Runtime.getRuntime().maxMemory() / (1024 * 1024));
        System.out.printf(Locale.ROOT, "test.png bytes=%d%n", png.length);

        CycleResult standard = runCycle("standard", () -> readWithStandardImageIo(png));
        CycleResult jwuff = runCycle("jwuff", () -> readWithJwuff(png));

        printTable(standard, jwuff);

        if (standard.allocatedBytes >= 0 && jwuff.allocatedBytes >= 0) {
            assertTrue(jwuff.allocatedBytes <= standard.allocatedBytes * 1.10,
                    "Expected jwuff allocated bytes not worse than standard by >10% (env-dependent): jwuff=" +
                            jwuff.allocatedBytes + ", standard=" + standard.allocatedBytes);
        }
        assertTrue(standard.afterDropGcUsedBytes < 512L * 1024 * 1024,
                "Expected standard heap used after drop+GC < 512 MiB, got: " + fmtBytes(standard.afterDropGcUsedBytes));
        assertTrue(jwuff.afterDropGcUsedBytes < 512L * 1024 * 1024,
                "Expected jwuff heap used after drop+GC < 512 MiB, got: " + fmtBytes(jwuff.afterDropGcUsedBytes));
    }

    private static BufferedImage readWithJwuff(byte[] bytes) {
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

    private static void printTable(CycleResult standard, CycleResult jwuff) {
        String[][] rows = new String[][]{
                {"gc + measure(before)", fmtBytes(standard.beforeUsedBytes), fmtBytes(jwuff.beforeUsedBytes)},
                {"read + measure(after)", fmtBytes(standard.afterReadUsedBytes), fmtBytes(jwuff.afterReadUsedBytes)},
                {"drop + gc + measure(after)", fmtBytes(standard.afterDropGcUsedBytes), fmtBytes(jwuff.afterDropGcUsedBytes)},
                {"allocated bytes (ThreadMXBean)", standard.allocatedBytes >= 0 ? fmtBytes(standard.allocatedBytes) : "n/a", jwuff.allocatedBytes >= 0 ? fmtBytes(jwuff.allocatedBytes) : "n/a"},
                {"read time", standard.readMillis + " ms", jwuff.readMillis + " ms"},
        };

        int c0 = "step".length();
        int c1 = standard.label.length();
        int c2 = jwuff.label.length();
        for (String[] r : rows) {
            c0 = Math.max(c0, r[0].length());
            c1 = Math.max(c1, r[1].length());
            c2 = Math.max(c2, r[2].length());
        }

        System.out.println();
        System.out.printf(Locale.ROOT, "%-" + c0 + "s | %-" + c1 + "s | %-" + c2 + "s%n", "step", standard.label, jwuff.label);
        System.out.printf(Locale.ROOT, "%s-+-%s-+-%s%n", "-".repeat(c0), "-".repeat(c1), "-".repeat(c2));
        for (String[] r : rows) {
            System.out.printf(Locale.ROOT, "%-" + c0 + "s | %-" + c1 + "s | %-" + c2 + "s%n", r[0], r[1], r[2]);
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
