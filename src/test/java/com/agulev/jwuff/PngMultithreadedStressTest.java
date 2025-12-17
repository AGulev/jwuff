package com.agulev.jwuff;

import com.agulev.jwuff.spi.WuffsPngImageReaderSpi;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PngMultithreadedStressTest {
    @Test
    void decodesManyPngsConcurrently_andPixelsMatch() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("jwuff.stress"),
                "Enable with -Djwuff.stress=true (stress test).");
        Assumptions.assumeTrue(isMacArm64(), "macOS arm64 only");

        List<TestPng> pngs = generateTestPngs();

        int threads = Math.min(Runtime.getRuntime().availableProcessors(), 8);
        int rounds = 3;
        int tasks = pngs.size() * rounds;
        System.out.printf(Locale.ROOT, "PngMultithreadedStressTest: threads=%d pngs=%d rounds=%d tasks=%d%n",
                threads, pngs.size(), rounds, tasks);

        ExecutorService exec = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Void>> callables = new ArrayList<>(tasks);
        for (int r = 0; r < rounds; r++) {
            for (TestPng t : pngs) {
                callables.add(() -> {
                    start.await();
                    decodeAndValidate(t);
                    return null;
                });
            }
        }

        List<Future<Void>> futures = new ArrayList<>(tasks);
        for (Callable<Void> c : callables) {
            futures.add(exec.submit(c));
        }

        long t0 = System.nanoTime();
        start.countDown();
        for (Future<Void> f : futures) {
            f.get();
        }
        long elapsedMs = Duration.ofNanos(System.nanoTime() - t0).toMillis();

        exec.shutdownNow();
        System.out.printf(Locale.ROOT, "PngMultithreadedStressTest: completed %d decodes in %d ms%n", tasks, elapsedMs);
    }

    private static void decodeAndValidate(TestPng t) throws Exception {
        WuffsPngImageReaderSpi spi = new WuffsPngImageReaderSpi();
        ImageReader reader = spi.createReaderInstance();
        try (ImageInputStream iis = javax.imageio.ImageIO.createImageInputStream(new ByteArrayInputStream(t.bytes))) {
            assertNotNull(iis);
            reader.setInput(iis, false, true);
            BufferedImage img = reader.read(0);
            assertNotNull(img);
            assertEquals(t.w, img.getWidth());
            assertEquals(t.h, img.getHeight());

            for (Sample s : t.samples) {
                assertEquals(s.argb, img.getRGB(s.x, s.y),
                        "pixel mismatch at " + s.x + "," + s.y + " for " + t.w + "x" + t.h);
            }
        } finally {
            reader.dispose();
        }
    }

    private static List<TestPng> generateTestPngs() throws Exception {
        int[][] sizes = new int[][]{
                {1, 1}, {2, 3}, {3, 2}, {7, 5}, {16, 16},
                {31, 17}, {64, 64}, {127, 33}, {128, 128}, {256, 64},
        };

        List<TestPng> out = new ArrayList<>(sizes.length);
        for (int[] s : sizes) {
            int w = s[0];
            int h = s[1];
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int a = 0xFF;
                    int r = (x * 31 + y * 17) & 0xFF;
                    int g = (x * 13 + y * 7) & 0xFF;
                    int b = (x * 3 + y * 11) & 0xFF;
                    int argb = (a << 24) | (r << 16) | (g << 8) | b;
                    img.setRGB(x, y, argb);
                }
            }

            byte[] pngBytes;
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                boolean ok = javax.imageio.ImageIO.write(img, "png", baos);
                if (!ok) throw new IllegalStateException("No PNG writer available");
                pngBytes = baos.toByteArray();
            }

            List<Sample> samples = List.of(
                    sampleAt(img, 0, 0),
                    sampleAt(img, w - 1, 0),
                    sampleAt(img, 0, h - 1),
                    sampleAt(img, w - 1, h - 1),
                    sampleAt(img, w / 2, h / 2)
            );
            out.add(new TestPng(w, h, pngBytes, samples));
        }
        return out;
    }

    private static Sample sampleAt(BufferedImage img, int x, int y) {
        return new Sample(x, y, img.getRGB(x, y));
    }

    private static boolean isMacArm64() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return osName.contains("mac") && (arch.equals("aarch64") || arch.equals("arm64"));
    }

    private record TestPng(int w, int h, byte[] bytes, List<Sample> samples) {}

    private record Sample(int x, int y, int argb) {}
}

