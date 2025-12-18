package com.agulev.jwuff;

import com.agulev.jwuff.nativelib.WuffsFFI;
import com.agulev.jwuff.nativelib.WuffsTypes;
import com.agulev.jwuff.model.FrameResult;
import com.agulev.jwuff.model.ProbeResult;
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
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformanceComparisonAlwaysTest {
    @Test
    void jwuffIsFasterThanStandardImageIo_onTestPerfAlwaysPng() throws Exception {
        byte[] png = readResourceBytes("/images/test_perf_always.png");

        ImageIO.scanForPlugins();
        WuffsFFI.probe(png);

        double standardMs = medianDecodeMs(5, () -> readStandardImageIo(png));
        double jwuffMs = medianDecodeMs(5, () -> readWuffsPng(png));

        // Variant-level checks (baseline vs AVX2 where available). This avoids forcing AVX2 globally, which would
        // break compatibility on older x86_64 CPUs.
        VariantResults variants = runNativeVariantChecks(png);

        System.out.printf("test_perf_always.png performance (median ms)%n");
        System.out.printf("%-20s %-12s %-12s %-12s%n", "reader", "standard", "jwuff", "native");
        System.out.printf("%-20s %-12.2f %-12.2f %-12s%n", "all platforms", standardMs, jwuffMs, "-");
        if (variants.baselineMs != null) {
            System.out.printf("%-20s %-12s %-12s %-12.2f%n", "baseline native", "-", "-", variants.baselineMs);
        }
        if (variants.avx2Ms != null) {
            System.out.printf("%-20s %-12s %-12s %-12.2f%n", "avx2 native", "-", "-", variants.avx2Ms);
        }

        assertTrue(jwuffMs * 1.10 <= standardMs,
                "Expected jwuff to be faster; standard=" + standardMs + "ms, jwuff=" + jwuffMs + "ms");
    }

    private static VariantResults runNativeVariantChecks(byte[] png) throws Exception {
        String baseResource = baseNativeResourcePathForCurrentPlatform();
        if (baseResource == null) {
            return new VariantResults(null, null);
        }

        boolean isX64 = isX86_64();
        String avx2Resource = isX64 ? avx2NativeResourcePath(baseResource) : null;

        try (Arena arena = Arena.ofConfined()) {
            NativeApi baseline = new NativeApi(extractResourceToTemp(baseResource), arena);
            ProbeResult probe = baseline.probe(png);
            byte[] dst = new byte[Math.multiplyExact(probe.height(), probe.strideBytes())];

            long baselineNs = measureNs(5, () -> {
                baseline.decodeFrameInto(png, dst);
                return null;
            });
            long baselineMedianNs = baselineNs; // `measureNs` already returns median across samples.
            long baselineCrc = crc32(dst);

            Double baselineMs = baselineMedianNs / 1_000_000.0;
            Double avx2Ms = null;

            if (avx2Resource != null) {
                boolean hasAvx2Resource = resourceExists(avx2Resource);
                if (hasAvx2Resource) {
                    boolean supportsAvx2 = baseline.cpuSupportsAvx2();
                    if (supportsAvx2) {
                        NativeApi avx2 = new NativeApi(extractResourceToTemp(avx2Resource), arena);
                        Arrays.fill(dst, (byte) 0);
                        long avx2MedianNs = measureNs(5, () -> {
                            avx2.decodeFrameInto(png, dst);
                            return null;
                        });
                        avx2Ms = avx2MedianNs / 1_000_000.0;
                        long avx2Crc = crc32(dst);
                        assertEquals(baselineCrc, avx2Crc, "baseline and avx2 must decode identical pixels");
                    }
                } else {
                    assertTrue(!isX64, "expected AVX2 native resource to be packaged on x86_64: " + avx2Resource);
                }
            }

            return new VariantResults(baselineMs, avx2Ms);
        }
    }

    private static boolean resourceExists(String path) {
        try (InputStream in = PerformanceComparisonAlwaysTest.class.getResourceAsStream(path)) {
            return in != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isX86_64() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return arch.equals("x86_64") || arch.equals("amd64") || arch.equals("x64");
    }

    private static String baseNativeResourcePathForCurrentPlatform() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);

        if (osName.contains("mac")) {
            if (arch.equals("aarch64") || arch.equals("arm64")) return "/natives/arm64-macos/libwuffs_imageio.dylib";
            if (isX86_64()) return "/natives/x86_64-macos/libwuffs_imageio.dylib";
            return null;
        }
        if (osName.contains("linux")) {
            if (isX86_64()) return "/natives/x86_64-linux/libwuffs_imageio.so";
            return null;
        }
        if (osName.contains("win")) {
            if (isX86_64()) return "/natives/x86_64-win32/wuffs_imageio.dll";
            return null;
        }
        return null;
    }

    private static String avx2NativeResourcePath(String baseResourcePath) {
        if (baseResourcePath.endsWith(".so")) return baseResourcePath.replace("libwuffs_imageio.so", "libwuffs_imageio_avx2.so");
        if (baseResourcePath.endsWith(".dylib")) return baseResourcePath.replace("libwuffs_imageio.dylib", "libwuffs_imageio_avx2.dylib");
        if (baseResourcePath.endsWith(".dll")) return baseResourcePath.replace("wuffs_imageio.dll", "wuffs_imageio_avx2.dll");
        return null;
    }

    private static java.nio.file.Path extractResourceToTemp(String resourcePath) throws Exception {
        String fileName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
        try (InputStream in = PerformanceComparisonAlwaysTest.class.getResourceAsStream(resourcePath)) {
            assertNotNull(in, "Missing native resource: " + resourcePath);
            java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("jwuff-test-natives-");
            dir.toFile().deleteOnExit();
            java.nio.file.Path out = dir.resolve(fileName);
            java.nio.file.Files.copy(in, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            out.toFile().deleteOnExit();
            return out;
        }
    }

    private static long crc32(byte[] bytes) {
        CRC32 crc = new CRC32();
        crc.update(bytes, 0, bytes.length);
        return crc.getValue();
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

    private static long measureNs(int samples, ThrowingSupplier<Void> op) throws Exception {
        long[] ns = new long[samples];
        for (int i = 0; i < samples; i++) {
            long start = System.nanoTime();
            op.get();
            ns[i] = System.nanoTime() - start;
        }
        Arrays.sort(ns);
        return ns[ns.length / 2];
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

    private record VariantResults(Double baselineMs, Double avx2Ms) {}

    private static final class NativeApi {
        private final Arena arena;
        private final Linker linker = Linker.nativeLinker();
        private final SymbolLookup symbols;
        private final java.lang.invoke.MethodHandle probe;
        private final java.lang.invoke.MethodHandle decode;
        private final java.lang.invoke.MethodHandle supportsAvx2;
        private final java.lang.invoke.MethodHandle errorMessage;

        NativeApi(java.nio.file.Path libraryPath, Arena arena) {
            this.arena = arena;
            this.symbols = SymbolLookup.libraryLookup(libraryPath, arena);

            this.probe = linker.downcallHandle(
                    symbols.find("wuffs_probe_image").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS),
                    Linker.Option.critical(true)
            );

            this.decode = linker.downcallHandle(
                    symbols.find("wuffs_decode_frame_into").orElseThrow(),
                    FunctionDescriptor.of(
                            ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS,
                            ValueLayout.JAVA_LONG,
                            ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS,
                            ValueLayout.JAVA_LONG,
                            ValueLayout.ADDRESS
                    ),
                    Linker.Option.critical(true)
            );

            this.supportsAvx2 = symbols.find("wuffs_cpu_supports_avx2")
                    .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_INT)))
                    .orElse(null);

            this.errorMessage = linker.downcallHandle(
                    symbols.find("wuffs_error_message").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
            );
        }

        ProbeResult probe(byte[] data) throws Exception {
            MemorySegment out = arena.allocate(WuffsTypes.PROBE_RESULT_LAYOUT);
            int code;
            try {
                code = (int) probe.invoke(MemorySegment.ofArray(data), (long) data.length, out);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
            if (code != 0) throw new IllegalStateException("probe failed: " + errorMessage(code));
            int width = (int) WuffsTypes.PROBE_WIDTH.get(out, 0L);
            int height = (int) WuffsTypes.PROBE_HEIGHT.get(out, 0L);
            int frameCount = (int) WuffsTypes.PROBE_FRAME_COUNT.get(out, 0L);
            int bytesPerPixel = (int) WuffsTypes.PROBE_BYTES_PER_PIXEL.get(out, 0L);
            int strideBytes = (int) WuffsTypes.PROBE_STRIDE_BYTES.get(out, 0L);
            return new ProbeResult(width, height, frameCount, bytesPerPixel, strideBytes);
        }

        FrameResult decodeFrameInto(byte[] data, byte[] dst) throws Exception {
            MemorySegment out = arena.allocate(WuffsTypes.FRAME_RESULT_LAYOUT);
            int code;
            try {
                code = (int) decode.invoke(
                        MemorySegment.ofArray(data),
                        (long) data.length,
                        0,
                        MemorySegment.NULL,
                        MemorySegment.ofArray(dst),
                        (long) dst.length,
                        out
                );
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
            if (code != 0) throw new IllegalStateException("decode failed: " + errorMessage(code));
            int width = (int) WuffsTypes.FRAME_WIDTH.get(out, 0L);
            int height = (int) WuffsTypes.FRAME_HEIGHT.get(out, 0L);
            int strideBytes = (int) WuffsTypes.FRAME_STRIDE_BYTES.get(out, 0L);
            int bytesWritten = (int) WuffsTypes.FRAME_BYTES_WRITTEN.get(out, 0L);
            return new FrameResult(width, height, strideBytes, bytesWritten);
        }

        boolean cpuSupportsAvx2() throws Exception {
            if (supportsAvx2 == null) return false;
            int res;
            try {
                res = (int) supportsAvx2.invokeExact();
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
            return res != 0;
        }

        String errorMessage(int code) {
            try {
                MemorySegment cString = (MemorySegment) errorMessage.invoke(code);
                if (cString == MemorySegment.NULL) return "unknown error";
                return cString.reinterpret(4096).getString(0);
            } catch (Throwable t) {
                return "unknown error";
            }
        }
    }
}
