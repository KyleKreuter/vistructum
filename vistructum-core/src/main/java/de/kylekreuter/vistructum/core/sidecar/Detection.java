package de.kylekreuter.vistructum.core.sidecar;

/** one flagged region in raster coordinates; {@code bottom} and {@code right} are exclusive. */
public record Detection(int top, int left, int bottom, int right, double score, int votes) {
}
