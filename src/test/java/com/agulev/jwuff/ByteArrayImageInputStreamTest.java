package com.agulev.jwuff;

import com.agulev.jwuff.io.ByteArrayImageInputStream;
import com.agulev.jwuff.spi.WuffsJpegImageReaderSpi;
import com.agulev.jwuff.spi.WuffsPngImageReaderSpi;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ByteArrayImageInputStreamTest {
    @Test
    void imageIoCreateImageInputStream_usesJwuffNoCopySpiForByteArray() throws Exception {
        byte[] bytes = readResource("/images/onepx.png");
        ImageIO.scanForPlugins();
        JwuffImageIO.register(true);

        try (ImageInputStream iis = ImageIO.createImageInputStream(bytes)) {
            assertNotNull(iis);
            assertTrue(iis instanceof ByteArrayImageInputStream, "Expected jwuff ByteArrayImageInputStream, got: " + iis.getClass());
        }
    }

    @Test
    void byteArrayImageInputStream_closeIsIdempotent() throws Exception {
        byte[] bytes = readResource("/images/onepx.png");
        ByteArrayImageInputStream iis = new ByteArrayImageInputStream(bytes);
        iis.close();
        iis.close(); // should not throw
    }

    @Test
    void pngDecodeWorksWithoutCopyingInputBytes() throws Exception {
        byte[] bytes = readResource("/images/onepx.png");
        try (ImageInputStream iis = new ByteArrayImageInputStream(bytes)) {
            WuffsPngImageReaderSpi spi = new WuffsPngImageReaderSpi();
            assertEquals(true, spi.canDecodeInput(iis));

            ImageReader reader = spi.createReaderInstance();
            try {
                iis.seek(0);
                reader.setInput(iis, false, true);
                BufferedImage img = reader.read(0);
                assertNotNull(img);
                assertEquals(1, img.getWidth());
                assertEquals(1, img.getHeight());
            } finally {
                reader.dispose();
            }
        }
    }

    @Test
    void jpegDecodeWorksWithoutCopyingInputBytes() throws Exception {
        byte[] bytes = readResource("/images/red16.jpg");
        try (ImageInputStream iis = new ByteArrayImageInputStream(bytes)) {
            WuffsJpegImageReaderSpi spi = new WuffsJpegImageReaderSpi();
            assertEquals(true, spi.canDecodeInput(iis));

            ImageReader reader = spi.createReaderInstance();
            try {
                iis.seek(0);
                reader.setInput(iis, false, true);
                BufferedImage img = reader.read(0);
                assertNotNull(img);
                assertEquals(16, img.getWidth());
                assertEquals(16, img.getHeight());
            } finally {
                reader.dispose();
            }
        }
    }

    @Test
    void jwuffImageIoHelperDecodesPngAndJpeg() throws Exception {
        assertNotNull(JwuffImageIO.read(readResource("/images/onepx.png")));
        assertNotNull(JwuffImageIO.read(readResource("/images/red16.jpg")));
    }

    private static byte[] readResource(String path) throws Exception {
        try (InputStream in = ByteArrayImageInputStreamTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "Missing resource: " + path);
            return in.readAllBytes();
        }
    }
}
