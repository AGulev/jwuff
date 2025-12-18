package com.agulev.jwuff;

import com.agulev.jwuff.nativelib.NativeLibrary;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class NativeResourceTest {
    @Test
    void dylibIsPresentOnClasspathResources() throws Exception {
        String basePath = NativeLibrary.resourcePathForCurrentPlatform();
        assertNotNull(basePath, "platform not supported by jwuff test");

        String avx2Path = null;
        if (basePath.endsWith(".so")) avx2Path = basePath.replace("libwuffs_imageio.so", "libwuffs_imageio_avx2.so");
        if (basePath.endsWith(".dylib")) avx2Path = basePath.replace("libwuffs_imageio.dylib", "libwuffs_imageio_avx2.dylib");
        if (basePath.endsWith(".dll")) avx2Path = basePath.replace("wuffs_imageio.dll", "wuffs_imageio_avx2.dll");

        try (InputStream in = NativeLibrary.class.getResourceAsStream(basePath)) {
            assertNotNull(in, "native library resource should be packaged on the classpath: " + basePath);
            assertNotNull(in.readNBytes(4));
        }

        if (avx2Path != null) {
            try (InputStream in = NativeLibrary.class.getResourceAsStream(avx2Path)) {
                // AVX2 variant is present only on x86_64 builds; don't require it on all platforms.
                if (in != null) {
                    assertNotNull(in.readNBytes(4));
                }
            }
        }
    }
}
