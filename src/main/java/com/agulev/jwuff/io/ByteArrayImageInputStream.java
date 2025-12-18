package com.agulev.jwuff.io;

import javax.imageio.stream.ImageInputStreamImpl;
import java.io.IOException;
import java.util.Objects;

/**
 * A seekable {@link javax.imageio.stream.ImageInputStream} backed by a {@code byte[]} without copying.
 *
 * <p>This is useful when you already have image bytes in memory and want to avoid the extra buffering/copying that
 * {@code ImageIO.read(InputStream)} may introduce via {@code MemoryCacheImageInputStream}.</p>
 */
public final class ByteArrayImageInputStream extends ImageInputStreamImpl {
    private final byte[] data;
    private final int offset;
    private final int length;
    private boolean closeCalled;

    public ByteArrayImageInputStream(byte[] data) {
        this(data, 0, data == null ? 0 : data.length);
    }

    public ByteArrayImageInputStream(byte[] data, int offset, int length) {
        this.data = Objects.requireNonNull(data, "data");
        if (offset < 0 || length < 0 || offset > data.length || (offset + length) > data.length) {
            throw new IllegalArgumentException("Invalid offset/length for array: offset=" + offset + ", length=" + length);
        }
        this.offset = offset;
        this.length = length;
    }

    /**
     * Returns the backing array (no copy).
     */
    public byte[] array() {
        return data;
    }

    /**
     * Returns the start offset (in {@link #array()}) for this stream.
     */
    public int arrayOffset() {
        return offset;
    }

    /**
     * Returns the number of readable bytes for this stream.
     */
    public int arrayLength() {
        return length;
    }

    @Override
    public int read() throws IOException {
        checkClosed();
        bitOffset = 0;
        if (streamPos >= length) {
            return -1;
        }
        int index = offset + (int) streamPos;
        streamPos++;
        return data[index] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        checkClosed();
        Objects.checkFromIndexSize(off, len, b.length);
        bitOffset = 0;
        if (len == 0) return 0;

        long remaining = (long) length - streamPos;
        if (remaining <= 0) return -1;
        int n = (int) Math.min(remaining, len);
        System.arraycopy(data, offset + (int) streamPos, b, off, n);
        streamPos += n;
        return n;
    }

    @Override
    public void seek(long pos) throws IOException {
        checkClosed();
        if (pos < flushedPos) {
            throw new IndexOutOfBoundsException("pos < flushedPos");
        }
        if (pos < 0 || pos > length) {
            throw new IndexOutOfBoundsException("pos out of range: " + pos);
        }
        streamPos = pos;
        bitOffset = 0;
    }

    @Override
    public long length() {
        return length;
    }

    @Override
    public void close() throws IOException {
        // Some pipelines (and some ImageIO readers) may close the stream multiple times.
        // Make close idempotent to avoid spurious build failures.
        if (closeCalled) return;
        closeCalled = true;
        try {
            super.close();
        } catch (IOException e) {
            // If the superclass considers it already closed, treat it as a no-op.
            if (!"closed".equalsIgnoreCase(e.getMessage())) {
                throw e;
            }
        }
    }
}
