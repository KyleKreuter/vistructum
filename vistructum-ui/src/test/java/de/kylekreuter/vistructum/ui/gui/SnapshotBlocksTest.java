package de.kylekreuter.vistructum.ui.gui;

import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotBlocksTest {

    @Test
    void surfaceIsTheHighestNonAirBlockAcrossSections() {
        assertEquals(70, blocks(Map.of("3,70,5", Material.STONE, "3,2,5", Material.DIRT)).surfaceY(19, -11));
        assertEquals(2, blocks(Map.of("3,2,5", Material.DIRT)).surfaceY(19, -11));
    }

    @Test
    void columnWithoutBlocksLiesBelowTheWorld() {
        assertEquals(-65, blocks(Map.of()).surfaceY(19, -11));
    }

    @Test
    void chunksOutsideTheSnapshotAreEmpty() {
        SnapshotBlocks blocks = blocks(Map.of("3,70,5", Material.STONE));
        assertEquals(-65, blocks.surfaceY(100, 100));
        assertTrue(blocks.isEmpty(100, 70, 100));
        assertFalse(blocks.isEmpty(19, 70, -11));
        assertTrue(blocks.isEmpty(19, 400, -11));
    }

    private static SnapshotBlocks blocks(Map<String, Material> blocks) {
        Set<Integer> filled = blocks.keySet().stream()
                .map(key -> Math.floorDiv(Integer.parseInt(key.split(",")[1]) + 64, 16))
                .collect(Collectors.toSet());
        ChunkSnapshot chunk = (ChunkSnapshot) Proxy.newProxyInstance(ChunkSnapshot.class.getClassLoader(),
                new Class<?>[]{ChunkSnapshot.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "isSectionEmpty" -> !filled.contains((Integer) args[0]);
                    case "getBlockType" -> blocks.getOrDefault(args[0] + "," + args[1] + "," + args[2], Material.AIR);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return new SnapshotBlocks(-64, 320, Map.of(SnapshotBlocks.key(1, -1), chunk));
    }
}
