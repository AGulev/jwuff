package com.agulev.jwuff;

import com.agulev.jwuff.nativelib.NativeLibrary;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class NativeResourceTest {
    @Test
    void dylibIsPresentOnClasspathResources() throws Exception {
        String path = NativeLibrary.resourcePathForCurrentPlatform();
        assertNotNull(path, "platform not supported by jwuff test");
        try (InputStream in = NativeLibrary.class.getResourceAsStream(path)) {
            assertNotNull(in, "native dylib resource should be packaged on the classpath");
            assertNotNull(in.readNBytes(4));
        }
    }
}
