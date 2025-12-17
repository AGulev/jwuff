package com.agulev.jwuff.metadata;

import org.w3c.dom.Node;

import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;

public final class BasicImageMetadata extends IIOMetadata {
    public static final String STANDARD_FORMAT = "javax_imageio_1.0";

    private final int width;
    private final int height;
    private final int numChannels;

    public BasicImageMetadata(int width, int height, int numChannels) {
        this.width = width;
        this.height = height;
        this.numChannels = numChannels;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public Node getAsTree(String formatName) {
        if (!STANDARD_FORMAT.equals(formatName)) {
            throw new IllegalArgumentException("Unsupported metadata format: " + formatName);
        }

        IIOMetadataNode root = new IIOMetadataNode(STANDARD_FORMAT);

        IIOMetadataNode chroma = new IIOMetadataNode("Chroma");
        chroma.appendChild(node("ColorSpaceType", "name", "RGB"));
        chroma.appendChild(node("NumChannels", "value", Integer.toString(numChannels)));
        root.appendChild(chroma);

        IIOMetadataNode dimension = new IIOMetadataNode("Dimension");
        dimension.appendChild(node("ImageOrientation", "value", "Normal"));
        dimension.appendChild(node("HorizontalPixelSize", "value", "1.0"));
        dimension.appendChild(node("VerticalPixelSize", "value", "1.0"));
        dimension.appendChild(node("HorizontalPixelOffset", "value", "0.0"));
        dimension.appendChild(node("VerticalPixelOffset", "value", "0.0"));
        root.appendChild(dimension);

        IIOMetadataNode document = new IIOMetadataNode("Document");
        document.appendChild(node("ImageCreationTime", "value", "1970-01-01T00:00:00Z"));
        root.appendChild(document);

        IIOMetadataNode transparency = new IIOMetadataNode("Transparency");
        transparency.appendChild(node("Alpha", "value", numChannels == 4 ? "nonpremultiplied" : "none"));
        root.appendChild(transparency);

        IIOMetadataNode jwuff = new IIOMetadataNode("jwuff");
        jwuff.appendChild(node("Width", "value", Integer.toString(width)));
        jwuff.appendChild(node("Height", "value", Integer.toString(height)));
        root.appendChild(jwuff);

        return root;
    }

    @Override
    public void mergeTree(String formatName, Node root) {
        throw new IllegalStateException("read-only");
    }

    @Override
    public void reset() {
        throw new IllegalStateException("read-only");
    }

    private static IIOMetadataNode node(String name, String attr, String value) {
        IIOMetadataNode n = new IIOMetadataNode(name);
        n.setAttribute(attr, value);
        return n;
    }
}

