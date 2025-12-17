package com.agulev.jwuff.model;

public record ProbeResult(
        int width,
        int height,
        int frameCount,
        int bytesPerPixel,
        int strideBytes
) {}
