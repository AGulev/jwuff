package com.agulev.jwuff;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SpiRegistrationTest {
    @Test
    void registersAllWuffsReaderSpisViaServiceFile() {
        ImageIO.scanForPlugins();

        IIORegistry registry = IIORegistry.getDefaultInstance();
        Set<String> providers = new HashSet<>();
        registry.getServiceProviders(ImageReaderSpi.class, false).forEachRemaining(p -> providers.add(p.getClass().getName()));

        assertTrue(providers.contains("com.agulev.jwuff.spi.WuffsJpegImageReaderSpi"));
        assertTrue(providers.contains("com.agulev.jwuff.spi.WuffsPngImageReaderSpi"));
    }
}
