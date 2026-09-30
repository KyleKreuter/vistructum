package de.kylekreuter.vistructum.core.history;

import java.util.Objects;

public record BlockRestore(int x, int y, int z, String expected, String restored) {

    public enum Outcome { RESTORE, ALREADY_RESTORED, SKIP }

    public BlockRestore {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(restored, "restored");
    }

    public Outcome against(String liveMaterial) {
        if (liveMaterial.equals(material(expected))) {
            return Outcome.RESTORE;
        }
        return liveMaterial.equals(material(restored)) ? Outcome.ALREADY_RESTORED : Outcome.SKIP;
    }

    static String material(String blockData) {
        int states = blockData.indexOf('[');
        return states < 0 ? blockData : blockData.substring(0, states);
    }
}
