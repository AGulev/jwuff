package com.agulev.jwuff.nativelib;

public final class WuffsTypes {
    private WuffsTypes() {}

    public static final java.lang.foreign.MemoryLayout PROBE_RESULT_LAYOUT =
            java.lang.foreign.MemoryLayout.structLayout(
                    java.lang.foreign.ValueLayout.JAVA_INT.withName("width"),
                    java.lang.foreign.ValueLayout.JAVA_INT.withName("height"),
                    java.lang.foreign.ValueLayout.JAVA_INT.withName("frame_count"),
                    java.lang.foreign.ValueLayout.JAVA_INT.withName("bytes_per_pixel"),
                    java.lang.foreign.ValueLayout.JAVA_INT.withName("stride_bytes")
            );

    public static final java.lang.invoke.VarHandle PROBE_WIDTH =
            PROBE_RESULT_LAYOUT.varHandle(java.lang.foreign.MemoryLayout.PathElement.groupElement("width"));
    public static final java.lang.invoke.VarHandle PROBE_HEIGHT =
            PROBE_RESULT_LAYOUT.varHandle(java.lang.foreign.MemoryLayout.PathElement.groupElement("height"));
    public static final java.lang.invoke.VarHandle PROBE_FRAME_COUNT =
            PROBE_RESULT_LAYOUT.varHandle(java.lang.foreign.MemoryLayout.PathElement.groupElement("frame_count"));
    public static final java.lang.invoke.VarHandle PROBE_BYTES_PER_PIXEL =
            PROBE_RESULT_LAYOUT.varHandle(java.lang.foreign.MemoryLayout.PathElement.groupElement("bytes_per_pixel"));
    public static final java.lang.invoke.VarHandle PROBE_STRIDE_BYTES =
            PROBE_RESULT_LAYOUT.varHandle(java.lang.foreign.MemoryLayout.PathElement.groupElement("stride_bytes"));

    public static final java.lang.foreign.MemoryLayout FRAME_RESULT_LAYOUT =
            java.lang.foreign.MemoryLayout.structLayout(
                    java.lang.foreign.ValueLayout.JAVA_INT.withName("width"),
                    java.lang.foreign.ValueLayout.JAVA_INT.withName("height"),
                    java.lang.foreign.ValueLayout.JAVA_INT.withName("stride_bytes"),
                    java.lang.foreign.ValueLayout.JAVA_INT.withName("bytes_written")
            );

    public static final java.lang.invoke.VarHandle FRAME_WIDTH =
            FRAME_RESULT_LAYOUT.varHandle(java.lang.foreign.MemoryLayout.PathElement.groupElement("width"));
    public static final java.lang.invoke.VarHandle FRAME_HEIGHT =
            FRAME_RESULT_LAYOUT.varHandle(java.lang.foreign.MemoryLayout.PathElement.groupElement("height"));
    public static final java.lang.invoke.VarHandle FRAME_STRIDE_BYTES =
            FRAME_RESULT_LAYOUT.varHandle(java.lang.foreign.MemoryLayout.PathElement.groupElement("stride_bytes"));
    public static final java.lang.invoke.VarHandle FRAME_BYTES_WRITTEN =
            FRAME_RESULT_LAYOUT.varHandle(java.lang.foreign.MemoryLayout.PathElement.groupElement("bytes_written"));
}
