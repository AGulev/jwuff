package com.agulev.jwuff;

import com.agulev.jwuff.metadata.BasicImageMetadata;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.io.InputStream;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MetadataTest {
    @Test
    void providesStandardMetadataTreeForPng() throws Exception {
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
                IIOMetadata meta = reader.getImageMetadata(0);
                assertNotNull(meta);
                Node tree = meta.getAsTree(BasicImageMetadata.STANDARD_FORMAT);
                assertEquals(BasicImageMetadata.STANDARD_FORMAT, tree.getNodeName());
            }
        }
    }
}

