package de.kylekreuter.vistructum.ui.gui;

import de.kylekreuter.vistructum.api.PlayerFace;

import java.util.Arrays;

final class Faces {

    private Faces() {
    }

    static Picture of(PlayerFace face) {
        if (!face.hasSkin()) {
            return placeholder();
        }
        int[] rgb = face.pixels().stream().mapToInt(Integer::intValue).toArray();
        return new Picture(PlayerFace.SIZE, PlayerFace.SIZE, rgb);
    }

    static Picture placeholder() {
        int[] rgb = new int[64];
        Arrays.fill(rgb, 0x8A8A8A);
        rgb[4 * 8 + 2] = 0x4A4A4A;
        rgb[4 * 8 + 5] = 0x4A4A4A;
        return new Picture(8, 8, rgb);
    }
}
