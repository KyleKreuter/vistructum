package de.kylekreuter.vistructum.core.skin;

import de.kylekreuter.vistructum.api.PlayerFace;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;

public final class SkinImages {

    static final int SIZE = 64;
    static final int LEGACY_HEIGHT = 32;
    private static final int[][] LEGACY_LIMBS = {
            {4, 16, 16, 32, 4, 4}, {8, 16, 16, 32, 4, 4}, {0, 20, 24, 32, 4, 12}, {4, 20, 16, 32, 4, 12},
            {8, 20, 8, 32, 4, 12}, {12, 20, 16, 32, 4, 12}, {44, 16, -8, 32, 4, 4}, {48, 16, -8, 32, 4, 4},
            {40, 20, 0, 32, 4, 12}, {44, 20, -8, 32, 4, 12}, {48, 20, -16, 32, 4, 12}, {52, 20, -8, 32, 4, 12}
    };

    private SkinImages() {
    }

    public static List<Integer> face(byte[] png) {
        return face(read(png));
    }

    static List<Integer> face(BufferedImage skin) {
        List<Integer> pixels = new ArrayList<>(PlayerFace.SIZE * PlayerFace.SIZE);
        for (int row = 0; row < PlayerFace.SIZE; row++) {
            for (int col = 0; col < PlayerFace.SIZE; col++) {
                int base = skin.getRGB(8 + col, 8 + row);
                int hat = skin.getRGB(40 + col, 8 + row);
                pixels.add(((hat >>> 24) > 0 ? hat : base) & 0xFFFFFF);
            }
        }
        return pixels;
    }

    static Optional<byte[]> normalized(byte[] png) {
        BufferedImage image = read(png);
        if (image == null || image.getWidth() != SIZE) {
            return Optional.empty();
        }
        if (image.getHeight() == SIZE) {
            return Optional.of(png);
        }
        if (image.getHeight() != LEGACY_HEIGHT) {
            return Optional.empty();
        }
        BufferedImage modern = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < LEGACY_HEIGHT; y++) {
            for (int x = 0; x < SIZE; x++) {
                modern.setRGB(x, y, image.getRGB(x, y));
            }
        }
        for (int[] limb : LEGACY_LIMBS) {
            mirror(modern, limb[0], limb[1], limb[2], limb[3], limb[4], limb[5]);
        }
        return Optional.of(write(modern));
    }

    private static void mirror(BufferedImage image, int x, int y, int offsetX, int offsetY, int width, int height) {
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                image.setRGB(x + offsetX + width - 1 - col, y + offsetY + row, image.getRGB(x + col, y + row));
            }
        }
    }

    private static BufferedImage read(byte[] png) {
        try {
            return ImageIO.read(new ByteArrayInputStream(png));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] write(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
