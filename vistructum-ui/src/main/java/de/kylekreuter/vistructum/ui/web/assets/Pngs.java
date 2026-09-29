package de.kylekreuter.vistructum.ui.web.assets;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

public final class Pngs {

    private Pngs() {
    }

    public static byte[] scaled(int width, int height, int[] rgb, int factor) {
        BufferedImage image = new BufferedImage(width * factor, height * factor, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, rgb[(y / factor) * width + x / factor]);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
