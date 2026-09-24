package de.kylekreuter.vistructum.core.scene;

/** an inclusive axis-aligned block region: both {@code min} and {@code max} corners are part of the box. */
public record WorldBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
}
