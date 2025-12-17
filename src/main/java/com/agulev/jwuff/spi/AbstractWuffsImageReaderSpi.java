package com.agulev.jwuff.spi;

import javax.imageio.ImageReader;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import java.util.Locale;

abstract class AbstractWuffsImageReaderSpi extends ImageReaderSpi {
    protected static final Class<?>[] STANDARD_INPUT_TYPES = new Class<?>[]{ImageInputStream.class};

    protected AbstractWuffsImageReaderSpi(
            String[] names,
            String[] suffixes,
            String[] mimeTypes,
            String readerClassName
    ) {
        super(
                "jwuff",
                "0.1.0",
                names,
                suffixes,
                mimeTypes,
                readerClassName,
                STANDARD_INPUT_TYPES,
                null,
                false,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                null
        );
    }

    @Override
    public final ImageReader createReaderInstance(Object extension) {
        return create();
    }

    protected abstract ImageReader create();

    @Override
    public String getDescription(Locale locale) {
        return "Wuffs-based ImageIO reader (FFM)";
    }
}
