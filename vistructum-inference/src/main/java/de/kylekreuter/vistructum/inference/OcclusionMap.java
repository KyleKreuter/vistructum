package de.kylekreuter.vistructum.inference;

import java.util.Objects;

public record OcclusionMap(String modelVersion, int width, int height, int[] values) {

    public OcclusionMap {
        Objects.requireNonNull(modelVersion, "modelVersion");
        Objects.requireNonNull(values, "values");
        if (values.length != width * height) {
            throw new IllegalArgumentException("occlusion map " + width + "x" + height + " with " + values.length
                    + " values");
        }
    }
}
