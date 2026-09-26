package de.kylekreuter.vistructum.core.region;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

public record PaletteEntry(String name, String properties) {

    public static final PaletteEntry AIR = new PaletteEntry("minecraft:air", "");

    private static final Set<String> AIR_NAMES = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air");

    public PaletteEntry {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(properties, "properties");
    }

    public static PaletteEntry of(String name, Map<String, String> properties) {
        if (properties.isEmpty()) {
            return new PaletteEntry(name, "");
        }
        return new PaletteEntry(name, new TreeMap<>(properties).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(",", "[", "]")));
    }

    public static PaletteEntry parse(String state) {
        int bracket = state.indexOf('[');
        return bracket < 0 ? new PaletteEntry(state, "") : new PaletteEntry(state.substring(0, bracket),
                state.substring(bracket));
    }

    public boolean air() {
        return AIR_NAMES.contains(name);
    }

    public String state() {
        return name + properties;
    }
}
