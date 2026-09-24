package de.kylekreuter.vistructum.core.sidecar;

public record Detection(int top, int left, int bottom, int right, double score, int votes) {
}
