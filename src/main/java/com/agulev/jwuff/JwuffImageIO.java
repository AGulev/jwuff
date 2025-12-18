package com.agulev.jwuff;

import com.agulev.jwuff.io.ByteArrayImageInputStream;
import com.agulev.jwuff.spi.ByteArrayImageInputStreamSpi;
import com.agulev.jwuff.spi.WuffsJpegImageReaderSpi;
import com.agulev.jwuff.spi.WuffsPngImageReaderSpi;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageInputStreamSpi;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
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

        ByteArrayImageInputStreamSpi bytesIis = new ByteArrayImageInputStreamSpi();
        WuffsPngImageReaderSpi png = new WuffsPngImageReaderSpi();
        WuffsJpegImageReaderSpi jpeg = new WuffsJpegImageReaderSpi();

        registry.registerServiceProvider(bytesIis);
        registry.registerServiceProvider(png);
        registry.registerServiceProvider(jpeg);

        if (preferJwuff) {
            preferByteArrayImageInputStream(registry, bytesIis);
            preferOverKnownBuiltins(registry, png, jpeg);
        }
    }

    private static void preferByteArrayImageInputStream(IIORegistry registry, ImageInputStreamSpi bytesIis) {
        List<ImageInputStreamSpi> all = new ArrayList<>();
        registry.getServiceProviders(ImageInputStreamSpi.class, false).forEachRemaining(all::add);

        for (ImageInputStreamSpi spi : all) {
            if (spi == bytesIis) continue;
            Class<?> inputClass = spi.getInputClass();
            if (inputClass == byte[].class) {
                registry.setOrdering(ImageInputStreamSpi.class, bytesIis, spi);
            }
        }
    }

    /**
     * Creates a seekable {@link ImageInputStream} over {@code bytes} without copying.
     *
     * <p>Using {@code ImageIO.read(ImageInputStream)} with this stream avoids the extra buffering that
     * {@code ImageIO.read(InputStream)} may perform.</p>
     */
    public static ImageInputStream createImageInputStream(byte[] bytes) {
        if (bytes == null) throw new IllegalArgumentException("bytes == null");
        return new ByteArrayImageInputStream(bytes);
    }

    /**
     * Decodes {@code bytes} using jwuff's ImageReaders (PNG/JPEG) without copying the input bytes.
     *
     * <p>This does not rely on ImageIO plugin discovery; it instantiates jwuff SPIs directly.</p>
     */
    public static BufferedImage read(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("bytes is empty");

        try (ImageInputStream iis = createImageInputStream(bytes)) {
            ImageReaderSpi spi;
            WuffsPngImageReaderSpi png = new WuffsPngImageReaderSpi();
            if (png.canDecodeInput(iis)) {
                spi = png;
            } else {
                WuffsJpegImageReaderSpi jpeg = new WuffsJpegImageReaderSpi();
                if (jpeg.canDecodeInput(iis)) {
                    spi = jpeg;
                } else {
                    // Fall back to ImageIO's default pipeline if it's not PNG/JPEG.
                    iis.seek(0);
                    return ImageIO.read(iis);
                }
            }

            ImageReader reader = spi.createReaderInstance();
            try {
                iis.seek(0);
                reader.setInput(iis, false, true);
                return reader.read(0);
            } finally {
                reader.dispose();
            }
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
