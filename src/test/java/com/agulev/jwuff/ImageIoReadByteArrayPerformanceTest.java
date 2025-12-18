package com.agulev.jwuff;

import com.agulev.jwuff.nativelib.WuffsFFI;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Manual benchmark that uses {@link ImageIO#read(InputStream)} with {@link ByteArrayInputStream}.
 *
 * <p>Run with:
 * <pre>
 * ./gradlew --no-daemon test --tests com.agulev.jwuff.ImageIoReadByteArrayPerformanceTest --rerun-tasks -Djwuff.perf=true
 * </pre>
 */
class ImageIoReadByteArrayPerformanceTest {
    @Test
    void imageIoReadByteArrayInputStream_standardVsJwuff() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("jwuff.perf"),
                "Enable with -Djwuff.perf=true (performance test; can be slow/flaky).");

        String inputPath = System.getProperty("jwuff.perf.path");
        String label = inputPath != null ? inputPath : "classpath:/images/test.png";
        byte[] bytes = inputPath != null ? Files.readAllBytes(Path.of(inputPath)) : readResourceBytes("/images/test.png");
        var probe = WuffsFFI.probe(bytes);

        // Ensure jwuff SPI is registered in environments with limited service discovery.
        ImageIO.scanForPlugins();
        JwuffImageIO.register(true);

        int warmup = Integer.getInteger("jwuff.perf.warmup", 0);
        int iters = Integer.getInteger("jwuff.perf.iters", 1);
        double minRatio = Double.parseDouble(System.getProperty("jwuff.perf.minRatio", "1.5"));

        // Avoid disk-backed ImageIO cache, which can dominate timings for large inputs.
        boolean prevUseCache = ImageIO.getUseCache();
        ImageIO.setUseCache(false);
        try {
            String jwuffFirst = firstReaderClassName(bytes);
            Assumptions.assumeTrue(jwuffFirst != null && jwuffFirst.startsWith("com.agulev.jwuff."),
                    "jwuff not selected for image via ImageIO; first reader=" + jwuffFirst);

            String standardFirst = withWuffsProvidersDisabledForTest(() -> firstReaderClassName(bytes));

            long standardNs = measureNs(warmup, iters, () -> readStandardImageIo(bytes));

            // `readStandardImageIo` temporarily deregisters jwuff providers and then re-registers them,
            // which can reset provider ordering. Re-apply jwuff preference before measuring jwuff.
            JwuffImageIO.register(true);
            String jwuffFirstAfterStandard = firstReaderClassName(bytes);
            Assumptions.assumeTrue(jwuffFirstAfterStandard != null && jwuffFirstAfterStandard.startsWith("com.agulev.jwuff."),
                    "jwuff not selected after standard run; first reader=" + jwuffFirstAfterStandard);

            long jwuffNs = measureNs(warmup, iters, () -> readJwuffViaImageIoRead(bytes));

            double standardMs = standardNs / 1_000_000.0 / iters;
            double jwuffMs = jwuffNs / 1_000_000.0 / iters;
            System.out.printf(
                    "byte[] -> new ByteArrayInputStream -> ImageIO.read; %s=%dx%d%n",
                    label, probe.width(), probe.height()
            );
            System.out.printf("  first reader (standard): %s%n", String.valueOf(standardFirst));
            System.out.printf("  first reader (jwuff):    %s%n", String.valueOf(jwuffFirstAfterStandard));
            System.out.printf(
                    "  standard avg=%.2f ms; jwuff avg=%.2f ms%n",
                    standardMs, jwuffMs
            );

            if (Boolean.parseBoolean(System.getProperty("jwuff.perf.assertRatio", "false"))) {
                assertTrue(standardNs >= (long) (jwuffNs * minRatio),
                        "Expected jwuff to be >= " + minRatio + "x faster; standard=" + standardMs + "ms, jwuff=" + jwuffMs + "ms");
            }
        } finally {
            ImageIO.setUseCache(prevUseCache);
        }
    }

    private static BufferedImage readJwuffViaImageIoRead(byte[] bytes) throws Exception {
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            BufferedImage img = ImageIO.read(in);
            assertNotNull(img);
            return img;
        }
    }

    private static BufferedImage readStandardImageIo(byte[] bytes) throws Exception {
        return withWuffsProvidersDisabledForTest(() -> {
            try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
                return ImageIO.read(in);
            }
        });
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
        try (InputStream in = ImageIoReadByteArrayPerformanceTest.class.getResourceAsStream(path)) {
            Assumptions.assumeTrue(in != null, "Missing resource: " + path);
            return in.readAllBytes();
        }
    }

    private static String firstReaderClassName(byte[] bytes) throws Exception {
        try (var iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (iis == null) return null;
            var it = ImageIO.getImageReaders(iis);
            if (!it.hasNext()) return null;
            var r = it.next();
            String name = r.getClass().getName();
            r.dispose();
            return name;
        }
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
            // Ensure provider ordering is restored for subsequent ImageIO.read calls.
            JwuffImageIO.register(true);
        }
    }

    @FunctionalInterface
    interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
