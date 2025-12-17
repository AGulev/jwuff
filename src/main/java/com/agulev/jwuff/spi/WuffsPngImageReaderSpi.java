package com.agulev.jwuff.spi;

import com.agulev.jwuff.reader.WuffsPngImageReader;

import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;

public final class WuffsPngImageReaderSpi extends AbstractWuffsImageReaderSpi {
    public WuffsPngImageReaderSpi() {
        super(
                new String[]{"PNG", "png"},
                new String[]{"png"},
                new String[]{"image/png"},
                WuffsPngImageReader.class.getName()
        );
    }

    @Override
    public boolean canDecodeInput(Object source) throws IOException {
        if (!(source instanceof ImageInputStream stream)) return false;
        return WuffsMagic.isPng(stream);
    }

    @Override
    protected ImageReader create() {
        return new WuffsPngImageReader(this);
    }
}
