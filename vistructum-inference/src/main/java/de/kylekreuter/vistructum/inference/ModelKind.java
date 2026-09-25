package de.kylekreuter.vistructum.inference;

import java.util.Arrays;
import java.util.Optional;

public enum ModelKind {

    MASK("mask", new int[]{0}),
    FULLSCAN("fullscan", new int[]{Contract.HEIGHT_CLIP, 0, Contract.LUMINANCE_PAD, 0});

    private final String id;
    private final int[] padValues;

    ModelKind(String id, int[] padValues) {
        this.id = id;
        this.padValues = padValues;
    }

    public String id() {
        return id;
    }

    public int channels() {
        return padValues.length;
    }

    int padValue(int channel) {
        return padValues[channel];
    }

    public static Optional<ModelKind> byId(String id) {
        return Arrays.stream(values()).filter(kind -> kind.id.equals(id)).findFirst();
    }
}
