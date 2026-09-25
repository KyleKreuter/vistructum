package de.kylekreuter.vistructum.ui.gui;

import org.bukkit.Color;
import org.bukkit.HeightMap;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Arrays;

record Picture(int width, int height, int[] rgb) {

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
