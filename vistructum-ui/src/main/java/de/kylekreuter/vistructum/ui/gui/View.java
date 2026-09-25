package de.kylekreuter.vistructum.ui.gui;

import de.kylekreuter.vistructum.api.BlockBox;

enum View {

    TOP,
    ALONG_X,
    ALONG_Z;

    static View of(BlockBox box) {
        int width = box.maxX() - box.minX();
        int height = box.maxY() - box.minY();
        int depth = box.maxZ() - box.minZ();
        if (height <= Math.min(width, depth)) {
            return TOP;
        }
        return depth <= width ? ALONG_Z : ALONG_X;
    }

    int planeWidth(BlockBox box) {
        return this == ALONG_X ? box.maxZ() - box.minZ() : box.maxX() - box.minX();
    }

    int planeHeight(BlockBox box) {
        return this == TOP ? box.maxZ() - box.minZ() : box.maxY() - box.minY();
    }
}
