package com.agulev.jwuff;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.InputStream;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZeroCopyPngTest {
    @Test
    void pngDecodeIsBackedBySingleByteArray() throws Exception {
        ImageIO.scanForPlugins();

        try (InputStream in = getClass().getResourceAsStream("/images/onepx.png")) {
            assertNotNull(in);
            try (ImageInputStream iis = ImageIO.createImageInputStream(in)) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
                ImageReader reader = null;
                while (readers.hasNext()) {
                    ImageReader r = readers.next();
                    if (r.getClass().getName().equals("com.agulev.jwuff.reader.WuffsPngImageReader")) {
                        reader = r;
                        break;
                    }
                }
                assertNotNull(reader);

                reader.setInput(iis, false, true);
                BufferedImage image = reader.read(0);
                assertEquals(1, image.getWidth());
                assertEquals(1, image.getHeight());

                assertTrue(image.getRaster().getDataBuffer() instanceof DataBufferByte);
                DataBufferByte db = (DataBufferByte) image.getRaster().getDataBuffer();
                byte[] backing = db.getData();
                assertEquals(4, backing.length);
                assertSame(backing, db.getData());

                assertEquals(0xFFFF0000, image.getRGB(0, 0));

                int before = image.getRGB(0, 0);
                backing[2] = 0;
                int after = image.getRGB(0, 0);
                assertTrue(before != after, "Mutating backing byte[] should affect image pixels (no extra pixel copy)");
            }
        }
    }
}
