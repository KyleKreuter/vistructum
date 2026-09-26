package de.kylekreuter.vistructum.core.scan;

import de.kylekreuter.vistructum.core.region.PaletteEntry;
import de.kylekreuter.vistructum.core.scene.Luminance;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PaperBlockStates implements BlockStates {

    private static final StateInfo UNKNOWN = new StateInfo((short) Material.AIR.ordinal(), false, false, 0);

    private final Map<PaletteEntry, StateInfo> infos = new ConcurrentHashMap<>();

    @Override
    public StateInfo info(PaletteEntry entry) {
        return infos.computeIfAbsent(entry, PaperBlockStates::resolve);
    }

    public boolean solid(String name) {
        return info(new PaletteEntry(name, "")).solid();
    }

    private static StateInfo resolve(PaletteEntry entry) {
        BlockData data;
        try {
            data = Bukkit.createBlockData(entry.state());
        } catch (IllegalArgumentException e) {
            Material material = Material.matchMaterial(entry.name());
            if (material == null || !material.isBlock()) {
                return UNKNOWN;
            }
            data = material.createBlockData();
        }
        Material material = data.getMaterial();
        return new StateInfo((short) material.ordinal(), isSurface(data), material.isSolid(),
                Luminance.of(data.getMapColor()));
    }

    static boolean isSurface(BlockData data) {
        Material material = data.getMaterial();
        return material.isSolid() || material == Material.WATER || material == Material.LAVA
                || data instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
    }
}
