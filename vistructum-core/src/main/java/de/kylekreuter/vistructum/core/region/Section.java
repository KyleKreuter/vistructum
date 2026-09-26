package de.kylekreuter.vistructum.core.region;

import java.util.List;

public record Section(int y, List<PaletteEntry> palette, long[] data) {

    public static final int SIDE = 16;
    public static final int VOLUME = SIDE * SIDE * SIDE;

    public Section {
        palette = List.copyOf(palette);
        if (palette.isEmpty()) {
            throw new IllegalArgumentException("section " + y + " has an empty palette");
        }
        if (palette.size() > 1 && data.length < requiredLongs(palette.size())) {
            throw new IllegalArgumentException("section " + y + " has " + data.length + " longs for "
                    + palette.size() + " palette entries");
        }
    }

    public static Section pack(int y, List<PaletteEntry> palette, int[] indices) {
        if (indices.length != VOLUME) {
            throw new IllegalArgumentException("section " + y + " has " + indices.length + " blocks");
        }
        if (palette.size() == 1) {
            return new Section(y, palette, new long[0]);
        }
        int bits = bits(palette.size());
        int perLong = Long.SIZE / bits;
        long[] data = new long[requiredLongs(palette.size())];
        for (int i = 0; i < VOLUME; i++) {
            data[i / perLong] |= (long) indices[i] << ((i % perLong) * bits);
        }
        return new Section(y, palette, data);
    }

    public static int index(int x, int y, int z) {
        return (y * SIDE + z) * SIDE + x;
    }

    public int[] indices() {
        int[] indices = new int[VOLUME];
        if (palette.size() == 1) {
            return indices;
        }
        int bits = bits(palette.size());
        int perLong = Long.SIZE / bits;
        long mask = (1L << bits) - 1;
        for (int i = 0; i < VOLUME; i++) {
            long word = data[i / perLong];
            int value = (int) ((word >>> ((i % perLong) * bits)) & mask);
            indices[i] = value < palette.size() ? value : 0;
        }
        return indices;
    }

    private static int requiredLongs(int paletteSize) {
        int perLong = Long.SIZE / bits(paletteSize);
        return (VOLUME + perLong - 1) / perLong;
    }

    private static int bits(int paletteSize) {
        return Math.max(4, Integer.SIZE - Integer.numberOfLeadingZeros(paletteSize - 1));
    }
}
