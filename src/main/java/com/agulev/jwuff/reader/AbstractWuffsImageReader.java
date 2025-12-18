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
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.agulev.jwuff.io.ByteArrayImageInputStream;
import com.agulev.jwuff.model.ProbeResult;
import com.agulev.jwuff.metadata.BasicImageMetadata;
import com.agulev.jwuff.nativelib.WuffsFFI;
import com.agulev.jwuff.nativelib.WuffsException;

public abstract class AbstractWuffsImageReader extends ImageReader {
    private static final Logger LOG = Logger.getLogger(AbstractWuffsImageReader.class.getName());
    private static final boolean LOG_DECODE = Boolean.getBoolean("jwuff.log.decode");
    private ProbeResult probe;
    private InputData inputData;

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
            InputData in = inputData();
            WuffsFFI.decodeFrameInto(in.data, in.offset, in.length, imageIndex, pixels);
        } catch (WuffsException e) {
            throw new IIOException(e.getMessage(), e);
        }

        if (LOG_DECODE) {
            String format = "unknown";
            try {
                ImageReaderSpi spi = getOriginatingProvider();
                if (spi != null && spi.getFormatNames() != null && spi.getFormatNames().length > 0) {
                    format = spi.getFormatNames()[0].toLowerCase(Locale.ROOT);
                } else {
                    String simple = getClass().getSimpleName().toLowerCase(Locale.ROOT);
                    if (simple.contains("png")) format = "png";
                    if (simple.contains("jpeg") || simple.contains("jpg")) format = "jpeg";
                }
            } catch (RuntimeException ignored) {
            }
            String msg = "jwuff used to decode " + format + " image w:" + width + " h:" + height;
            LOG.log(Level.INFO, msg);
        }
        return toBufferedImageBgraNonPremul(width, height, rowBytes, pixels);
    }

    @Override
    public void setInput(Object input, boolean seekForwardOnly, boolean ignoreMetadata) {
        super.setInput(input, seekForwardOnly, ignoreMetadata);
        this.probe = null;
        this.inputData = null;
    }

    @Override
    public void setLocale(Locale locale) {
        super.setLocale(locale);
    }

    protected final ProbeResult probe() {
        ProbeResult cached = probe;
        if (cached != null) return cached;

        InputData in = inputData();
        ProbeResult result = WuffsFFI.probe(in.data, in.offset, in.length);
        this.probe = result;
        return result;
    }

    private InputData inputData() {
        InputData cached = inputData;
        if (cached != null) return cached;

        Object in = getInput();
        if (!(in instanceof ImageInputStream stream)) {
            throw new IllegalStateException("Expected ImageInputStream input");
        }

        if (stream instanceof ByteArrayImageInputStream bais) {
            inputData = new InputData(bais.array(), bais.arrayOffset(), bais.arrayLength());
            return inputData;
        }

        byte[] bytes = readAllBytes(stream);
        inputData = new InputData(bytes, 0, bytes.length);
        return inputData;
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

            long len = -1;
            try {
                len = stream.length();
            } catch (IOException ignored) {
            }
            if (len >= 0 && len <= Integer.MAX_VALUE) {
                byte[] bytes = new byte[(int) len];
                stream.readFully(bytes);
                return bytes;
            }

            // Unknown length: read into fixed-size chunks, then join with a single final copy.
            List<byte[]> chunks = new ArrayList<>();
            int total = 0;
            byte[] buf = new byte[8192];
            while (true) {
                int n = stream.read(buf);
                if (n < 0) break;
                if (n == 0) continue;
                byte[] chunk = new byte[n];
                System.arraycopy(buf, 0, chunk, 0, n);
                chunks.add(chunk);
                total += n;
            }

            byte[] all = new byte[total];
            int at = 0;
            for (byte[] c : chunks) {
                System.arraycopy(c, 0, all, at, c.length);
                at += c.length;
            }
            return all;
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

    private record InputData(byte[] data, int offset, int length) {}
}
