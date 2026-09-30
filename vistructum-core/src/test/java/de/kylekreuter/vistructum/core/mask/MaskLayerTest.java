package de.kylekreuter.vistructum.core.mask;

import de.kylekreuter.vistructum.core.scene.Axis;
import de.kylekreuter.vistructum.core.scene.BlockPos;
import de.kylekreuter.vistructum.core.scene.MaskProjector;
import de.kylekreuter.vistructum.core.scene.Projection;
import de.kylekreuter.vistructum.core.tracking.ChangeKind;
import de.kylekreuter.vistructum.core.tracking.ChangedBlock;
import de.kylekreuter.vistructum.core.tracking.Cluster;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaskLayerTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final int MIN_BLOCKS = 12;
    private static final String[] SYMBOL = {
            "X.XXX",
            "X.X..",
            "XXXXX",
            "..X.X",
            "XXX.X"};
    private static final int SYMBOL_Z = -420;
    private static final int WALL_Z = -421;

    @Test
    void symbolInFrontOfAWoolWallGetsItsOwnLayer() {
        Cluster cluster = cluster(symbolOnWall());

        List<MaskLayer> layers = MaskLayer.of(cluster, cluster.placed(), Set.of(), MIN_BLOCKS);

        assertEquals(List.of(Optional.empty(), Optional.of("minecraft:black_wool"), Optional.of("minecraft:white_wool")),
                layers.stream().map(MaskLayer::material).toList());
        assertEquals(symbol(), layers.get(1).placed());
    }

    @Test
    void symbolLayerProjectsToTheSymbolWhereTheWholeClusterIsAFilledSquare() {
        Cluster cluster = cluster(symbolOnWall());
        List<MaskLayer> layers = MaskLayer.of(cluster, cluster.placed(), Set.of(), MIN_BLOCKS);
        MaskProjector projector = new MaskProjector(0);

        Projection all = projector.project(Axis.Z, layers.get(0).positions()).orElseThrow();
        Projection symbol = projector.project(Axis.Z, layers.get(1).positions()).orElseThrow();

        assertEquals("XXXXX".repeat(5), picture(all));
        assertEquals(String.join("", SYMBOL), picture(symbol));
    }

    @Test
    void singleMaterialClusterKeepsOneLayer() {
        Map<BlockPos, String> blocks = new HashMap<>();
        symbol().forEach(pos -> blocks.put(pos, "minecraft:birch_wood"));

        List<MaskLayer> layers = MaskLayer.of(cluster(blocks), symbol(), Set.of(), MIN_BLOCKS);

        assertEquals(1, layers.size());
        assertEquals(Optional.empty(), layers.getFirst().material());
    }

    @Test
    void materialsBelowMinBlocksGetNoLayer() {
        Map<BlockPos, String> blocks = new HashMap<>();
        symbol().forEach(pos -> blocks.put(pos, "minecraft:birch_wood"));
        blocks.put(new BlockPos(-3, 112, SYMBOL_Z), "minecraft:oak_door");
        blocks.put(new BlockPos(-3, 113, SYMBOL_Z), "minecraft:oak_door");
        Cluster cluster = cluster(blocks);

        List<MaskLayer> layers = MaskLayer.of(cluster, cluster.placed(), Set.of(), MIN_BLOCKS);

        assertEquals(List.of(Optional.empty(), Optional.of("minecraft:birch_wood")),
                layers.stream().map(MaskLayer::material).toList());
    }

    @Test
    void carvedBlocksJoinTheLayerOfTheRemovedMaterial() {
        Map<BlockPos, String> blocks = new HashMap<>(symbolOnWall());
        Set<BlockPos> carved = new HashSet<>();
        for (int i = 0; i < MIN_BLOCKS; i++) {
            BlockPos pos = new BlockPos(20 + i, 100, SYMBOL_Z);
            blocks.put(pos, "minecraft:stone");
            carved.add(pos);
        }
        Cluster cluster = cluster(blocks, carved);

        List<MaskLayer> layers = MaskLayer.of(cluster, cluster.placed(), carved, MIN_BLOCKS);

        MaskLayer stone = layers.stream().filter(layer -> layer.material().equals(Optional.of("minecraft:stone")))
                .findFirst().orElseThrow();
        assertEquals(carved, stone.carved());
        assertTrue(stone.placed().isEmpty());
    }

    @Test
    void tooFewBlocksGiveNoLayer() {
        Map<BlockPos, String> blocks = Map.of(new BlockPos(0, 0, 0), "minecraft:stone");

        assertTrue(MaskLayer.of(cluster(blocks), blocks.keySet(), Set.of(), MIN_BLOCKS).isEmpty());
    }

    @Test
    void detailNamesTheMaterialWithoutNamespace() {
        MaskLayer layer = new MaskLayer(Optional.of("minecraft:black_wool"), Set.of(), Set.of());

        assertEquals("Material black_wool, Achse Z", layer.detail(Axis.Z));
        assertEquals("Achse Z", new MaskLayer(Optional.empty(), Set.of(), Set.of()).detail(Axis.Z));
    }

    private static Set<BlockPos> symbol() {
        Set<BlockPos> symbol = new HashSet<>();
        for (int row = 0; row < SYMBOL.length; row++) {
            for (int col = 0; col < SYMBOL[row].length(); col++) {
                if (SYMBOL[row].charAt(col) == 'X') {
                    symbol.add(new BlockPos(col, 127 - row, SYMBOL_Z));
                }
            }
        }
        return symbol;
    }

    private static Map<BlockPos, String> symbolOnWall() {
        Map<BlockPos, String> blocks = new HashMap<>();
        for (int x = 0; x < 5; x++) {
            for (int y = 123; y <= 127; y++) {
                blocks.put(new BlockPos(x, y, WALL_Z), "minecraft:white_wool");
            }
        }
        symbol().forEach(pos -> blocks.put(pos, "minecraft:black_wool"));
        return blocks;
    }

    private static Cluster cluster(Map<BlockPos, String> materials) {
        return cluster(materials, Set.of());
    }

    private static Cluster cluster(Map<BlockPos, String> materials, Set<BlockPos> broken) {
        Map<BlockPos, ChangedBlock> blocks = new HashMap<>();
        materials.forEach((pos, material) -> blocks.put(pos, broken.contains(pos)
                ? new ChangedBlock(ChangeKind.BREAK, material, Set.of(), Set.of(PLAYER))
                : new ChangedBlock(ChangeKind.PLACE, material, Set.of(PLAYER), Set.of())));
        return new Cluster("world", blocks, new BlockPos(0, 0, 0), new BlockPos(0, 0, 0), 0, false);
    }

    private static String picture(Projection projection) {
        StringBuilder picture = new StringBuilder();
        for (byte cell : projection.scene().modified()) {
            picture.append(cell == 0 ? '.' : 'X');
        }
        return picture.toString();
    }
}
