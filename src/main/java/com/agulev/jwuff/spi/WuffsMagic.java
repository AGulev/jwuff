package com.agulev.jwuff.spi;

import javax.imageio.stream.ImageInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;

final class WuffsMagic {
    private WuffsMagic() {}

    static boolean hasPrefix(ImageInputStream stream, byte[] prefix) throws IOException {
        long pos = stream.getStreamPosition();
        try {
            byte[] buf = new byte[prefix.length];
            stream.readFully(buf);
            return Arrays.equals(buf, prefix);
        } catch (EOFException eof) {
            return false;
        } finally {
            stream.seek(pos);
        }
    }

    static boolean isJpeg(ImageInputStream stream) throws IOException {
        return hasPrefix(stream, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
    }

    static boolean isPng(ImageInputStream stream) throws IOException {
        return hasPrefix(stream, new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        });
    }
}
