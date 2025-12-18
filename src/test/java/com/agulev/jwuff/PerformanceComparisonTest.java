package com.agulev.jwuff;

import com.agulev.jwuff.nativelib.WuffsFFI;
import com.agulev.jwuff.spi.WuffsPngImageReaderSpi;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformanceComparisonTest {
    @Test
    void imageIoReadVsWuffsReader_isAtLeast2xFaster() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("jwuff.perf"),
                "Enable with -Djwuff.perf=true (performance test; can be slow/flaky).");
        Assumptions.assumeTrue(isMacArm64(), "macOS arm64 only");

        byte[] png = readResourceBytes("/images/test.png");
        var probe = WuffsFFI.probe(png);

        boolean prevUseCache = ImageIO.getUseCache();
        ImageIO.setUseCache(false);
        try {
            ImageIO.scanForPlugins();
            WuffsFFI.probe(png);

            int warmup = 1;
            int iters = 2;

            long standardNs = measureNs(warmup, iters, () -> readStandardImageIo(png));
            long wuffsNs = measureNs(warmup, iters, () -> readWuffsPng(png));

            double standardMs = standardNs / 1_000_000.0 / iters;
            double wuffsMs = wuffsNs / 1_000_000.0 / iters;
            System.out.printf(
                    "test.png=%dx%d; standard ImageIO.read avg=%.2f ms; jwuff avg=%.2f ms%n",
                    probe.width(), probe.height(), standardMs, wuffsMs
            );

            assertTrue(standardNs >= (wuffsNs * 2),
                    "Expected jwuff to be >= 2x faster; standard=" + standardMs + "ms, jwuff=" + wuffsMs + "ms");
        } finally {
            ImageIO.setUseCache(prevUseCache);
        }
    }

    private static BufferedImage readStandardImageIo(byte[] bytes) throws Exception {
        return withWuffsProvidersDisabledForTest(() -> ImageIO.read(new ByteArrayInputStream(bytes)));
    }

    private static BufferedImage readWuffsPng(byte[] bytes) throws Exception {
        ImageReaderSpi spi = new WuffsPngImageReaderSpi();
        ImageReader reader = spi.createReaderInstance();
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            reader.setInput(iis, false, true);
            return reader.read(0);
        } finally {
            reader.dispose();
        }
    }

    private static long measureNs(int warmupIters, int measureIters, ThrowingSupplier<BufferedImage> op) throws Exception {
        for (int i = 0; i < warmupIters; i++) {
            BufferedImage img = op.get();
            Assumptions.assumeTrue(img != null);
        }

        long start = System.nanoTime();
        for (int i = 0; i < measureIters; i++) {
            BufferedImage img = op.get();
            Assumptions.assumeTrue(img != null);
        }
        return System.nanoTime() - start;
    }

    private static byte[] readResourceBytes(String path) throws Exception {
        try (InputStream in = PerformanceComparisonTest.class.getResourceAsStream(path)) {
            Assumptions.assumeTrue(in != null, "Missing resource: " + path);
            return in.readAllBytes();
        }
    }

    private static boolean isMacArm64() {
        String osName = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(java.util.Locale.ROOT);
        return osName.contains("mac") && (arch.equals("aarch64") || arch.equals("arm64"));
    }

    static <T> T withWuffsProvidersDisabledForTest(ThrowingSupplier<T> op) throws Exception {
        IIORegistry registry = IIORegistry.getDefaultInstance();
        List<ImageReaderSpi> disabled = new ArrayList<>();
        registry.getServiceProviders(ImageReaderSpi.class, false).forEachRemaining(p -> {
            if (p.getClass().getName().startsWith("com.agulev.jwuff.spi.")) {
                disabled.add(p);
            }
        });

        for (ImageReaderSpi p : disabled) {
            registry.deregisterServiceProvider(p);
        }
        try {
            return op.get();
        } finally {
            for (ImageReaderSpi p : disabled) {
                registry.registerServiceProvider(p);
            }
        }
    }

    @FunctionalInterface
    interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
