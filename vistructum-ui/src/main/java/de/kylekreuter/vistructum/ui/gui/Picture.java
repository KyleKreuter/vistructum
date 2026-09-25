package de.kylekreuter.vistructum.ui.gui;

import de.kylekreuter.vistructum.api.BlockBox;
import org.bukkit.Color;
import org.bukkit.HeightMap;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Arrays;

record Picture(int width, int height, int[] rgb) {

    static final int BACKDROP = 16;
    private static final int SKY = 0xA4C2F4;
    private static final double NEAR_SHADE = 1.0;
    private static final double BACKDROP_SHADE = 180 / 255.0;

    static Picture surface(World world, int centreX, int centreZ, int size) {
        int[] rgb = new int[size * size];
        int left = centreX - size / 2;
        int top = centreZ - size / 2;
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                int x = left + col;
                int z = top + row;
                Block surface = world.getHighestBlockAt(x, z, HeightMap.WORLD_SURFACE);
                int north = world.getHighestBlockYAt(x, z - 1, HeightMap.WORLD_SURFACE);
                double shade = surface.getY() > north ? 1.0 : surface.getY() == north ? 220 / 255.0 : 180 / 255.0;
                Color color = surface.getBlockData().getMapColor();
                rgb[row * size + col] = pack(color.getRed() * shade, color.getGreen() * shade, color.getBlue() * shade);
            }
        }
        return new Picture(size, size, rgb);
    }

    static Picture side(World world, BlockBox box, View view, int size) {
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
                if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
                    continue;
                }
                for (int along = front; along <= back + BACKDROP; along++) {
                    Block block = alongX ? world.getBlockAt(along, y, across) : world.getBlockAt(across, y, along);
                    if (!block.isEmpty()) {
                        Color color = block.getBlockData().getMapColor();
                        double shade = along <= back ? NEAR_SHADE : BACKDROP_SHADE;
                        rgb[row * size + col] = pack(color.getRed() * shade, color.getGreen() * shade,
                                color.getBlue() * shade);
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

    int at(int row, int col) {
        return rgb[row * width + col];
    }

    private static int pack(double r, double g, double b) {
        return ((int) r << 16) | ((int) g << 8) | (int) b;
    }
}
