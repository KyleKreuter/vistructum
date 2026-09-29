package de.kylekreuter.vistructum.core.skin;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinImagesTest {

    @Test
    void opaqueHatReplacesTheBaseLayer() {
        BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        skin.setRGB(8, 8, 0xFF112233);
        skin.setRGB(9, 8, 0xFF445566);
        skin.setRGB(41, 8, 0xFFAABBCC);
        assertEquals(0x112233, SkinImages.face(skin).get(0));
        assertEquals(0xAABBCC, SkinImages.face(skin).get(1));
        assertEquals(64, SkinImages.face(skin).size());
    }

    @Test
    void modernSkinIsKeptAsItIs() {
        byte[] png = skin(64, 0xFF010203);
        assertArrayEquals(png, SkinImages.normalized(png).orElseThrow());
    }

    @Test
    void legacySkinGetsMirroredLeftLimbs() throws IOException {
        BufferedImage legacy = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        legacy.setRGB(0, 20, 0xFF00FF00);
        legacy.setRGB(40, 20, 0xFFFF0000);
        legacy.setRGB(8, 8, 0xFF112233);

        BufferedImage modern = ImageIO.read(new ByteArrayInputStream(SkinImages.normalized(png(legacy)).orElseThrow()));

        assertEquals(64, modern.getHeight());
        assertEquals(0xFF112233, modern.getRGB(8, 8));
        assertEquals(0xFF00FF00, modern.getRGB(24 + 3, 52));
        assertEquals(0xFFFF0000, modern.getRGB(40 + 3, 52));
    }

    @Test
    void otherSizesAreNoSkin() {
        assertTrue(SkinImages.normalized(png(new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB))).isEmpty());
    }

    static byte[] skin(int height, int argb) {
        BufferedImage image = new BufferedImage(64, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < 64; x++) {
                image.setRGB(x, y, argb);
            }
        }
        return png(image);
    }

    static byte[] png(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
