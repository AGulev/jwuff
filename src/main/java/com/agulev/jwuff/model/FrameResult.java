package com.agulev.jwuff.model;

public record FrameResult(
        int width,
        int height,
        int strideBytes,
        int bytesWritten
) {}
