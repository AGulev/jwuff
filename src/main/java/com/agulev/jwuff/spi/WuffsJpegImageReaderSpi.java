package com.agulev.jwuff.spi;

import com.agulev.jwuff.reader.WuffsJpegImageReader;

import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;

public final class WuffsJpegImageReaderSpi extends AbstractWuffsImageReaderSpi {
    public WuffsJpegImageReaderSpi() {
        super(
                new String[]{"JPEG", "JPG", "jpeg", "jpg"},
                new String[]{"jpg", "jpeg"},
                new String[]{"image/jpeg"},
                WuffsJpegImageReader.class.getName()
        );
    }

    @Override
    public boolean canDecodeInput(Object source) throws IOException {
        if (!(source instanceof ImageInputStream stream)) return false;
        return WuffsMagic.isJpeg(stream);
    }

    @Override
    protected ImageReader create() {
        return new WuffsJpegImageReader(this);
    }
}
