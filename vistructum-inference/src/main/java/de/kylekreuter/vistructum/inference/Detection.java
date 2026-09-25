package de.kylekreuter.vistructum.inference;

public record Detection(int top, int left, int bottom, int right, double score, int votes) {
}
