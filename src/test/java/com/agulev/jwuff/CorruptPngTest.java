package com.agulev.jwuff;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorruptPngTest {
    @Test
    void corruptPngFailsWithIIOException() throws Exception {
        ImageIO.scanForPlugins();

        byte[] bytes;
        try (InputStream in = getClass().getResourceAsStream("/images/onepx.png")) {
            assertNotNull(in);
            bytes = in.readAllBytes();
        }

        byte[] corrupt = bytes.clone();
        corruptFirstIdatByte(corrupt);

        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(corrupt))) {
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
            ImageReader finalReader = reader;

            reader.setInput(iis, false, true);
            var ex = assertThrows(javax.imageio.IIOException.class, () -> finalReader.read(0));
            String msg = String.valueOf(ex.getMessage()).toLowerCase(java.util.Locale.ROOT);
            assertTrue(msg.contains("png") || msg.contains("checksum") || msg.contains("wuffs"),
                    "message should mention png/checksum/wuffs; got: " + ex.getMessage());
        }
    }

    private static void corruptFirstIdatByte(byte[] png) {
        if (png.length < 8) {
            png[png.length - 1] ^= 0xFF;
            return;
        }
        ByteBuffer bb = ByteBuffer.wrap(png).order(ByteOrder.BIG_ENDIAN);
        int off = 8;
        while (off + 8 <= png.length) {
            int len = bb.getInt(off);
            if (len < 0) return;
            int typeOff = off + 4;
            if (typeOff + 4 > png.length) return;
            String type = new String(png, typeOff, 4, java.nio.charset.StandardCharsets.US_ASCII);
            int dataOff = off + 8;
            int crcOff = dataOff + len;
            int next = crcOff + 4;
            if (dataOff < 0 || crcOff < 0 || next < 0) return;
            if (next > png.length) return;
            if ("IDAT".equals(type) && len > 0) {
                png[dataOff] ^= 0x01;
                return;
            }
            off = next;
        }
        png[png.length - 1] ^= 0xFF;
    }
}
