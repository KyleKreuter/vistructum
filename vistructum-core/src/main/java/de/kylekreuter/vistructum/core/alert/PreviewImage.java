package de.kylekreuter.vistructum.core.alert;

import de.kylekreuter.vistructum.api.Preview;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;

public final class PreviewImage {

    private PreviewImage() {
    }

    public static byte[] png(Preview preview, int scale) {
        if (scale < 1) {
            throw new IllegalArgumentException("scale must be >= 1, got " + scale);
        }
        BufferedImage image = new BufferedImage(preview.width() * scale, preview.height() * scale,
                BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.getRaster().setSample(x, y, 0, preview.grey(y / scale, x / scale));
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
