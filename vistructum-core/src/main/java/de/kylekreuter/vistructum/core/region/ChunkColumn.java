package de.kylekreuter.vistructum.core.region;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record ChunkColumn(int chunkX, int chunkZ, int dataVersion, String status, List<Section> sections,
                          Optional<long[]> motionBlocking) {

    public static final String FULL = "minecraft:full";

    private static final Set<String> SKIPPED = Set.of("block_entities", "structures", "PostProcessing",
            "blending_data", "block_ticks", "fluid_ticks", "biomes", "BlockLight", "SkyLight", "UpgradeData",
            "CarvingMask", "Lights", "entities");

    public ChunkColumn {
        sections = List.copyOf(sections);
    }

    public boolean full() {
        return FULL.equals(status);
    }

    public static ChunkColumn decode(int chunkX, int chunkZ, byte[] nbt) throws IOException {
        Map<String, Object> root = Nbt.read(nbt, SKIPPED);
        int dataVersion = root.get("DataVersion") instanceof Integer version ? version : 0;
        String status = root.get("Status") instanceof String value ? value : "";
        List<Section> sections = new ArrayList<>();
        if (root.get("sections") instanceof List<?> list) {
            for (Object element : list) {
                if (element instanceof Map<?, ?> section) {
                    section(section).ifPresent(sections::add);
                }
            }
        }
        Optional<long[]> motionBlocking = root.get("Heightmaps") instanceof Map<?, ?> heightmaps
                && heightmaps.get("MOTION_BLOCKING") instanceof long[] values ? Optional.of(values) : Optional.empty();
        return new ChunkColumn(chunkX, chunkZ, dataVersion, status, sections, motionBlocking);
    }

    private static Optional<Section> section(Map<?, ?> section) {
        if (!(section.get("Y") instanceof Byte y) || !(section.get("block_states") instanceof Map<?, ?> states)
                || !(states.get("palette") instanceof List<?> entries)) {
            return Optional.empty();
        }
        List<String> palette = new ArrayList<>(entries.size());
        for (Object entry : entries) {
            palette.add(entry instanceof Map<?, ?> state && state.get("Name") instanceof String name ? name
                    : "minecraft:air");
        }
        long[] data = states.get("data") instanceof long[] values ? values : new long[0];
        return Optional.of(new Section(y, palette, data));
    }
}
