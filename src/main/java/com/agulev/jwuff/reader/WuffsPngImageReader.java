package com.agulev.jwuff.reader;

import javax.imageio.spi.ImageReaderSpi;

public final class WuffsPngImageReader extends AbstractWuffsImageReader {
    public WuffsPngImageReader(ImageReaderSpi originatingProvider) {
        super(originatingProvider);
    }
}
