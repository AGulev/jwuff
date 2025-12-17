package com.agulev.jwuff;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ImageIoReadIntegrationTest {
    @Test
    void imageIoReadDecodesPngViaJwuffPlugin() throws Exception {
        ImageIO.scanForPlugins();
        try (InputStream in = getClass().getResourceAsStream("/images/onepx.png")) {
            assertNotNull(in);
            BufferedImage image = ImageIO.read(in);
            assertNotNull(image);
            assertEquals(1, image.getWidth());
            assertEquals(1, image.getHeight());
            assertEquals(0xFFFF0000, image.getRGB(0, 0));
        }
    }

    @Test
    void imageIoReadDecodesJpegViaJwuffPlugin() throws Exception {
        ImageIO.scanForPlugins();
        try (InputStream in = getClass().getResourceAsStream("/images/red16.jpg")) {
            assertNotNull(in);
            BufferedImage image = ImageIO.read(in);
            assertNotNull(image);
            assertEquals(16, image.getWidth());
            assertEquals(16, image.getHeight());
        }
    }
}

