package com.agulev.jwuff.nativelib;

import com.agulev.jwuff.model.FrameResult;
import com.agulev.jwuff.model.ProbeResult;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

public final class WuffsFFI {
    private static final Arena ARENA = Arena.ofShared();
    private static final Linker LINKER = Linker.nativeLinker();
    private static volatile SymbolLookup lookup;
    private static volatile MethodHandle probeHandle;
    private static volatile MethodHandle decodeHandle;
    private static volatile MethodHandle errorMessageHandle;

    private WuffsFFI() {}

    public static SymbolLookup symbols() {
        SymbolLookup current = lookup;
        if (current != null) return current;

        synchronized (WuffsFFI.class) {
            current = lookup;
            if (current != null) return current;
            NativeLibrary.load();
            lookup = SymbolLookup.libraryLookup(NativeLibrary.loadedLibraryPath(), ARENA);
            return lookup;
        }
    }

    public static Linker linker() {
        return LINKER;
    }

    public static ProbeResult probe(byte[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("data is empty");
        }

        MethodHandle mh = probeMethodHandle();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(WuffsTypes.PROBE_RESULT_LAYOUT);
            int code = (int) mh.invoke(MemorySegment.ofArray(data), (long) data.length, out);
            if (code != 0) {
                throw new WuffsException(code, "wuffs_probe_image failed: " + errorMessage(code) + " (" + code + ")");
            }

            int width = (int) WuffsTypes.PROBE_WIDTH.get(out, 0L);
            int height = (int) WuffsTypes.PROBE_HEIGHT.get(out, 0L);
            int frameCount = (int) WuffsTypes.PROBE_FRAME_COUNT.get(out, 0L);
            int bytesPerPixel = (int) WuffsTypes.PROBE_BYTES_PER_PIXEL.get(out, 0L);
            int strideBytes = (int) WuffsTypes.PROBE_STRIDE_BYTES.get(out, 0L);
            return new ProbeResult(width, height, frameCount, bytesPerPixel, strideBytes);
        } catch (Throwable t) {
            if (t instanceof RuntimeException re) throw re;
            throw new RuntimeException(t);
        }
    }

    public static String errorMessage(int code) {
        MethodHandle mh = errorMessageMethodHandle();
        try {
            MemorySegment cString = (MemorySegment) mh.invoke(code);
            if (cString == MemorySegment.NULL) return "unknown error";
            return cString.reinterpret(4096).getString(0);
        } catch (Throwable t) {
            return "unknown error";
        }
    }

    public static FrameResult decodeFrameInto(byte[] data, int frameIndex, byte[] dstPixels) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("data is empty");
        }
        if (dstPixels == null || dstPixels.length == 0) {
            throw new IllegalArgumentException("dstPixels is empty");
        }

        MethodHandle mh = decodeMethodHandle();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(WuffsTypes.FRAME_RESULT_LAYOUT);
            int code = (int) mh.invoke(
                    MemorySegment.ofArray(data),
                    (long) data.length,
                    frameIndex,
                    MemorySegment.NULL,
                    MemorySegment.ofArray(dstPixels),
                    (long) dstPixels.length,
                    out
            );
            if (code != 0) {
                throw new WuffsException(code, "wuffs_decode_frame_into failed: " + errorMessage(code) + " (" + code + ")");
            }

            int width = (int) WuffsTypes.FRAME_WIDTH.get(out, 0L);
            int height = (int) WuffsTypes.FRAME_HEIGHT.get(out, 0L);
            int strideBytes = (int) WuffsTypes.FRAME_STRIDE_BYTES.get(out, 0L);
            int bytesWritten = (int) WuffsTypes.FRAME_BYTES_WRITTEN.get(out, 0L);
            return new FrameResult(width, height, strideBytes, bytesWritten);
        } catch (Throwable t) {
            if (t instanceof RuntimeException re) throw re;
            throw new RuntimeException(t);
        }
    }

    private static MethodHandle probeMethodHandle() {
        MethodHandle current = probeHandle;
        if (current != null) return current;

        synchronized (WuffsFFI.class) {
            current = probeHandle;
            if (current != null) return current;
            var symbol = symbols().find("wuffs_probe_image").orElseThrow();
            probeHandle = linker().downcallHandle(
                    symbol,
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS),
                    Linker.Option.critical(true)
            );
            return probeHandle;
        }
    }

    private static MethodHandle decodeMethodHandle() {
        MethodHandle current = decodeHandle;
        if (current != null) return current;

        synchronized (WuffsFFI.class) {
            current = decodeHandle;
            if (current != null) return current;
            var symbol = symbols().find("wuffs_decode_frame_into").orElseThrow();
            decodeHandle = linker().downcallHandle(
                    symbol,
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
            return decodeHandle;
        }
    }

    private static MethodHandle errorMessageMethodHandle() {
        MethodHandle current = errorMessageHandle;
        if (current != null) return current;

        synchronized (WuffsFFI.class) {
            current = errorMessageHandle;
            if (current != null) return current;
            var symbol = symbols().find("wuffs_error_message").orElseThrow();
            errorMessageHandle = linker().downcallHandle(
                    symbol,
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
            );
            return errorMessageHandle;
        }
    }
}
