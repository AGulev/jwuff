package com.agulev.jwuff.spi;

import com.agulev.jwuff.io.ByteArrayImageInputStream;

import javax.imageio.spi.ImageInputStreamSpi;
import javax.imageio.stream.ImageInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Produces a no-copy {@link ImageInputStream} for {@code byte[]} inputs.
 */
public final class ByteArrayImageInputStreamSpi extends ImageInputStreamSpi {
    public ByteArrayImageInputStreamSpi() {
        super("com.agulev", "1.0", byte[].class);
    }

    @Override
    public String getDescription(Locale locale) {
        return "jwuff byte[] ImageInputStream (no-copy)";
    }

    @Override
    public ImageInputStream createInputStreamInstance(Object input, boolean useCache, File cacheDir) throws IOException {
        if (!(input instanceof byte[] bytes)) {
            throw new IllegalArgumentException("Expected byte[] input");
        }
        return new ByteArrayImageInputStream(bytes);
    }
}
