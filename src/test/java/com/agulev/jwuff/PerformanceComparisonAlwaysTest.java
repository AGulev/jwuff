package com.agulev.jwuff;

import com.agulev.jwuff.nativelib.WuffsFFI;
import com.agulev.jwuff.spi.WuffsPngImageReaderSpi;
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
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformanceComparisonAlwaysTest {
    @Test
    void jwuffIsFasterThanStandardImageIo_onTestPerfAlwaysPng() throws Exception {
        byte[] png = readResourceBytes("/images/test_perf_always.png");

        ImageIO.scanForPlugins();
        WuffsFFI.probe(png);

        double standardMs = medianDecodeMs(5, () -> readStandardImageIo(png));
        double jwuffMs = medianDecodeMs(5, () -> readWuffsPng(png));

        System.out.printf(
                "test_perf_always.png; standard ImageIO.read median=%.2f ms; jwuff median=%.2f ms%n",
                standardMs, jwuffMs
        );

        assertTrue(jwuffMs * 1.10 <= standardMs,
                "Expected jwuff to be faster; standard=" + standardMs + "ms, jwuff=" + jwuffMs + "ms");
    }

    private static double medianDecodeMs(int samples, ThrowingSupplier<BufferedImage> op) throws Exception {
        double[] ms = new double[samples];
        for (int i = 0; i < samples; i++) {
            ms[i] = measurePerDecodeMs(op);
        }
        Arrays.sort(ms);
        return ms[ms.length / 2];
    }

    private static double measurePerDecodeMs(ThrowingSupplier<BufferedImage> op) throws Exception {
        BufferedImage warm = op.get();
        assertNotNull(warm);

        int iters = 1;
        long totalNs = measureTotalNs(iters, op);
        while (totalNs < 50_000_000L && iters < 64) {
            iters *= 2;
            totalNs = measureTotalNs(iters, op);
        }
        return (totalNs / 1_000_000.0) / iters;
    }

    private static long measureTotalNs(int iters, ThrowingSupplier<BufferedImage> op) throws Exception {
        long start = System.nanoTime();
        for (int i = 0; i < iters; i++) {
            BufferedImage img = op.get();
            assertNotNull(img);
        }
        return System.nanoTime() - start;
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

    private static byte[] readResourceBytes(String path) throws Exception {
        try (InputStream in = PerformanceComparisonAlwaysTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "Missing test resource: " + path);
            return in.readAllBytes();
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
        }
    }

    @FunctionalInterface
    interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}

