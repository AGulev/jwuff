package com.agulev.jwuff;

import com.agulev.jwuff.spi.WuffsJpegImageReaderSpi;
import com.agulev.jwuff.spi.WuffsPngImageReaderSpi;

import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Helper to programmatically register jwuff ImageIO plugins.
 *
 * <p>Normally ImageIO discovers plugins via {@code META-INF/services}. Some environments
 * use custom classloaders or run with {@code java -jar ...}, which can prevent discovery.
 * Calling {@link #register()} makes jwuff available to {@code ImageIO.read(...)}.</p>
 */
public final class JwuffImageIO {
    private JwuffImageIO() {}

    /**
     * Registers jwuff PNG/JPEG ImageReader SPIs and prefers them over the JDK built-ins when possible.
     */
    public static void register() {
        register(true);
    }

    /**
     * Registers jwuff PNG/JPEG ImageReader SPIs.
     *
     * @param preferJwuff if true, orders jwuff readers before common built-in JDK readers.
     */
    public static void register(boolean preferJwuff) {
        IIORegistry registry = IIORegistry.getDefaultInstance();

        WuffsPngImageReaderSpi png = new WuffsPngImageReaderSpi();
        WuffsJpegImageReaderSpi jpeg = new WuffsJpegImageReaderSpi();
        registry.registerServiceProvider(png);
        registry.registerServiceProvider(jpeg);

        if (preferJwuff) {
            preferOverKnownBuiltins(registry, png, jpeg);
        }
    }

    private static void preferOverKnownBuiltins(IIORegistry registry, ImageReaderSpi png, ImageReaderSpi jpeg) {
        List<ImageReaderSpi> all = new ArrayList<>();
        registry.getServiceProviders(ImageReaderSpi.class, false).forEachRemaining(all::add);

        for (ImageReaderSpi spi : all) {
            String name = spi.getClass().getName();
            if (name.startsWith("com.agulev.jwuff.")) continue;

            String n = name.toLowerCase(Locale.ROOT);
            // JDK built-ins are typically com.sun.imageio.plugins.(png|jpeg).*Spi, but keep this generic.
            if (n.contains("png") && providesFormat(spi, "png")) {
                registry.setOrdering(ImageReaderSpi.class, png, spi);
            }
            if ((n.contains("jpeg") || n.contains("jpg")) && (providesFormat(spi, "jpeg") || providesFormat(spi, "jpg"))) {
                registry.setOrdering(ImageReaderSpi.class, jpeg, spi);
            }
        }
    }

    private static boolean providesFormat(ImageReaderSpi spi, String fmt) {
        String[] names = spi.getFormatNames();
        if (names == null) return false;
        for (String n : names) {
            if (n != null && n.equalsIgnoreCase(fmt)) return true;
        }
        return false;
    }
}

