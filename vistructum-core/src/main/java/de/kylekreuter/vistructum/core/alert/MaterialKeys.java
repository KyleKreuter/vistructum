package de.kylekreuter.vistructum.core.alert;

import org.bukkit.Material;

import java.util.Optional;

public final class MaterialKeys {

    private static final Material[] MATERIALS = Material.values();

    private MaterialKeys() {
    }

    public static Optional<String> byOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= MATERIALS.length || MATERIALS[ordinal].isLegacy()) {
            return Optional.empty();
        }
        return Optional.of(MATERIALS[ordinal].getKey().toString());
    }
}
