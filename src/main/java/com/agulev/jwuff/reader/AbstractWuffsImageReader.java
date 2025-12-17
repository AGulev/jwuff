package com.agulev.jwuff.reader;

import javax.imageio.IIOException;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;

import com.agulev.jwuff.model.ProbeResult;
import com.agulev.jwuff.metadata.BasicImageMetadata;
import com.agulev.jwuff.nativelib.WuffsFFI;
import com.agulev.jwuff.nativelib.WuffsException;

public abstract class AbstractWuffsImageReader extends ImageReader {
    private ProbeResult probe;
    private byte[] inputBytes;

    protected AbstractWuffsImageReader(ImageReaderSpi originatingProvider) {
        super(originatingProvider);
    }

    @Override
    public int getNumImages(boolean allowSearch) {
        return probe().frameCount();
    }

    @Override
    public Iterator<ImageTypeSpecifier> getImageTypes(int imageIndex) throws IOException {
        return Collections.singleton(ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_4BYTE_ABGR)).iterator();
    }

    @Override
    public int getWidth(int imageIndex) throws IOException {
        try {
            return probe().width();
        } catch (WuffsException e) {
            throw new IIOException(e.getMessage(), e);
        }
    }

    @Override
    public int getHeight(int imageIndex) throws IOException {
        try {
            return probe().height();
        } catch (WuffsException e) {
            throw new IIOException(e.getMessage(), e);
        }
    }

    @Override
    public IIOMetadata getStreamMetadata() {
        return null;
    }

    @Override
    public IIOMetadata getImageMetadata(int imageIndex) {
        if (imageIndex != 0) {
            throw new IndexOutOfBoundsException("imageIndex must be 0");
        }
        try {
            ProbeResult p = probe();
            return new BasicImageMetadata(p.width(), p.height(), 4);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public BufferedImage read(int imageIndex, ImageReadParam param) throws IOException {
        if (imageIndex != 0) {
            throw new IndexOutOfBoundsException("imageIndex must be 0");
        }

        ProbeResult p;
        try {
            p = probe();
        } catch (WuffsException e) {
            throw new IIOException(e.getMessage(), e);
        }
        int width = p.width();
        int height = p.height();
        int rowBytes = Math.multiplyExact(width, 4);
        int pixelLen = Math.multiplyExact(rowBytes, height);

        byte[] pixels = new byte[pixelLen];
        try {
            WuffsFFI.decodeFrameInto(inputBytes(), imageIndex, pixels);
        } catch (WuffsException e) {
            throw new IIOException(e.getMessage(), e);
        }
        return toBufferedImageBgraNonPremul(width, height, rowBytes, pixels);
    }

    @Override
    public void setInput(Object input, boolean seekForwardOnly, boolean ignoreMetadata) {
        super.setInput(input, seekForwardOnly, ignoreMetadata);
        this.probe = null;
        this.inputBytes = null;
    }

    @Override
    public void setLocale(Locale locale) {
        super.setLocale(locale);
    }

    protected final ProbeResult probe() {
        ProbeResult cached = probe;
        if (cached != null) return cached;

        ProbeResult result = WuffsFFI.probe(inputBytes());
        this.probe = result;
        return result;
    }

    protected final byte[] inputBytes() {
        byte[] cached = inputBytes;
        if (cached != null) return cached;

        Object in = getInput();
        if (!(in instanceof ImageInputStream stream)) {
            throw new IllegalStateException("Expected ImageInputStream input");
        }

        inputBytes = readAllBytes(stream);
        return inputBytes;
    }

    private static byte[] readAllBytes(ImageInputStream stream) {
        long pos;
        try {
            pos = stream.getStreamPosition();
        } catch (IOException e) {
            pos = -1;
        }

        try {
            if (pos != 0) {
                stream.seek(0);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            while (true) {
                int n = stream.read(buf);
                if (n < 0) break;
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read ImageInputStream", e);
        } finally {
            if (pos >= 0) {
                try {
                    stream.seek(pos);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static BufferedImage toBufferedImageBgraNonPremul(int width, int height, int strideBytes, byte[] pixels) {
        DataBufferByte db = new DataBufferByte(pixels, pixels.length);
        int[] bandOffsets = new int[]{2, 1, 0, 3};
        WritableRaster raster = Raster.createInterleavedRaster(
                db,
                width,
                height,
                strideBytes,
                4,
                bandOffsets,
                null
        );

        ComponentColorModel cm = new ComponentColorModel(
                ColorSpace.getInstance(ColorSpace.CS_sRGB),
                true,
                false,
                Transparency.TRANSLUCENT,
                DataBuffer.TYPE_BYTE
        );
        return new BufferedImage(cm, raster, false, null);
    }
}
