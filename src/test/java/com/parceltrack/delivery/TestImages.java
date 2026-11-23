package com.parceltrack.delivery;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Builds small synthetic images for upload tests. */
final class TestImages {

    private TestImages() {
    }

    static byte[] png(int width, int height) {
        return encode(width, height, "png");
    }

    static byte[] jpg(int width, int height) {
        return encode(width, height, "jpeg");
    }

    private static byte[] encode(int width, int height, String format) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
