package com.agulev.jwuff.reader;

import javax.imageio.spi.ImageReaderSpi;

public final class WuffsJpegImageReader extends AbstractWuffsImageReader {
    public WuffsJpegImageReader(ImageReaderSpi originatingProvider) {
        super(originatingProvider);
    }
}
