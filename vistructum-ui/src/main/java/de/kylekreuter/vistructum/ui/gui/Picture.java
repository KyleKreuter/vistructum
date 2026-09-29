package de.kylekreuter.vistructum.ui.gui;

import de.kylekreuter.vistructum.api.BlockBox;
import de.kylekreuter.vistructum.api.Thumbnail;

import java.util.Arrays;

record Picture(int width, int height, int[] rgb) {

    static final int BACKDROP = 16;
    private static final int SKY = 0xA4C2F4;
    private static final double NEAR_SHADE = 1.0;
    private static final double BACKDROP_SHADE = 180 / 255.0;

    static Picture of(Thumbnail thumbnail) {
        return new Picture(thumbnail.width(), thumbnail.height(), thumbnail.pixels());
    }

    static Picture surface(Blocks blocks, int centreX, int centreZ, int size) {
        int[] rgb = new int[size * size];
        int left = centreX - size / 2;
        int top = centreZ - size / 2;
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                int x = left + col;
                int z = top + row;
                int y = blocks.surfaceY(x, z);
                int north = blocks.surfaceY(x, z - 1);
                double shade = y > north ? 1.0 : y == north ? 220 / 255.0 : 180 / 255.0;
                rgb[row * size + col] = shaded(blocks.mapColor(x, y, z), shade);
            }
        }
        return new Picture(size, size, rgb);
    }

    static Picture side(Blocks blocks, BlockBox box, View view, int size) {
        boolean alongX = view == View.ALONG_X;
        int firstColumn = (alongX ? box.centerZ() : box.centerX()) - size / 2;
        int topY = Math.floorDiv(box.minY() + box.maxY(), 2) + size / 2;
        int front = alongX ? box.minX() : box.minZ();
        int back = alongX ? box.maxX() : box.maxZ();
        int[] rgb = new int[size * size];
        for (int row = 0; row < size; row++) {
            int y = topY - row;
            for (int col = 0; col < size; col++) {
                int across = alongX ? firstColumn + col : firstColumn + size - 1 - col;
                rgb[row * size + col] = SKY;
                if (y < blocks.minHeight() || y >= blocks.maxHeight()) {
                    continue;
                }
                for (int along = front; along <= back + BACKDROP; along++) {
                    int x = alongX ? along : across;
                    int z = alongX ? across : along;
                    if (!blocks.isEmpty(x, y, z)) {
                        rgb[row * size + col] = shaded(blocks.mapColor(x, y, z),
                                along <= back ? NEAR_SHADE : BACKDROP_SHADE);
                        break;
                    }
                }
            }
        }
        return new Picture(size, size, rgb);
    }

    static Picture solid(int size, int rgb) {
        int[] pixels = new int[size * size];
        Arrays.fill(pixels, rgb);
        return new Picture(size, size, pixels);
    }

    Picture resample(int size) {
        if (size == width && size == height) {
            return this;
        }
        int[] out = new int[size * size];
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                out[row * size + col] = at(row * height / size, col * width / size);
            }
        }
        return new Picture(size, size, out);
    }

    Thumbnail thumbnail() {
        return new Thumbnail(width, height, rgb);
    }

    int at(int row, int col) {
        return rgb[row * width + col];
    }

    private static int shaded(int rgb, double shade) {
        int r = (int) (((rgb >> 16) & 0xFF) * shade);
        int g = (int) (((rgb >> 8) & 0xFF) * shade);
        int b = (int) ((rgb & 0xFF) * shade);
        return (r << 16) | (g << 8) | b;
    }
}
