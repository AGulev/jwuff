package com.agulev.jwuff;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JpegDecodeTest {
    @Test
    void decodesJpegWithWuffsReader() throws Exception {
        ImageIO.scanForPlugins();

        try (InputStream in = getClass().getResourceAsStream("/images/red16.jpg")) {
            assertNotNull(in);
            try (ImageInputStream iis = ImageIO.createImageInputStream(in)) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
                ImageReader reader = null;
                while (readers.hasNext()) {
                    ImageReader r = readers.next();
                    if (r.getClass().getName().equals("com.agulev.jwuff.reader.WuffsJpegImageReader")) {
                        reader = r;
                        break;
                    }
                }
                assertNotNull(reader);

                reader.setInput(iis, false, true);
                BufferedImage image = reader.read(0);
                assertEquals(16, image.getWidth());
                assertEquals(16, image.getHeight());

                int argb = image.getRGB(0, 0);
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;

                assertTrue(r >= 240, "expected strong red channel, got: " + r);
                assertTrue(g <= 20, "expected low green channel, got: " + g);
                assertTrue(b <= 20, "expected low blue channel, got: " + b);
            }
        }
    }
}

