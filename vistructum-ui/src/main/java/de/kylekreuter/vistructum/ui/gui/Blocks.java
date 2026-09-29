package de.kylekreuter.vistructum.ui.gui;

interface Blocks {

    int minHeight();

    int maxHeight();

    int surfaceY(int x, int z);

    boolean isEmpty(int x, int y, int z);

    int mapColor(int x, int y, int z);
}
