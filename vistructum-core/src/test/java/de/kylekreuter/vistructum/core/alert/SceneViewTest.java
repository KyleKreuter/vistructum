package de.kylekreuter.vistructum.core.alert;

import de.kylekreuter.vistructum.api.FindingScene;
import de.kylekreuter.vistructum.api.Source;
import de.kylekreuter.vistructum.inference.ModelKind;
import de.kylekreuter.vistructum.inference.SurfaceScene;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SceneViewTest {

    private static final Map<Integer, String> KEYS = Map.of(3, "minecraft:dirt", 7, "minecraft:stone_bricks");

    @Test
    void maskScenesShowChangedCellsAsRaisedStone() {
        StoredScene stored = new StoredScene(1, Source.MASK, new ModelInput(ModelKind.MASK,
                SurfaceScene.maskOnly(2, 2, new byte[]{1, 0, 0, 1}), 0, 0, 2, 2));

        FindingScene scene = SceneView.of(stored, ordinal -> Optional.empty());

        assertEquals(List.of(SceneView.CHANGED_BLOCK), scene.palette());
        assertArrayEquals(new int[]{0, -1, -1, 0}, scene.blocks());
        assertArrayEquals(new int[]{1, 0, 0, 1}, scene.heights());
        assertArrayEquals(new int[]{255, 0, 0, 255}, scene.luminance());
        assertEquals(0, scene.windowTop());
        assertEquals(1, scene.windowBottom());
    }

    @Test
    void fullscanScenesMapOrdinalsToKeysAndHeightsToTheLowestKnownCell() {
        SurfaceScene surface = new SurfaceScene(3, 1, new short[]{7, SurfaceScene.UNKNOWN, 3},
                new short[]{70, 0, 64}, new byte[]{(byte) 200, 0, 10}, new byte[3]);
        StoredScene stored = new StoredScene(2, Source.FULLSCAN, new ModelInput(ModelKind.FULLSCAN, surface, 0, 1, 1, 3));

        FindingScene scene = SceneView.of(stored, ordinal -> Optional.ofNullable(KEYS.get(ordinal)));

        assertEquals(Source.FULLSCAN, scene.source());
        assertEquals(List.of("minecraft:stone_bricks", "minecraft:dirt"), scene.palette());
        assertArrayEquals(new int[]{0, -1, 1}, scene.blocks());
        assertArrayEquals(new int[]{6, 0, 0}, scene.heights());
        assertArrayEquals(new int[]{200, 0, 10}, scene.luminance());
        assertEquals(1, scene.windowLeft());
        assertEquals(2, scene.windowRight());
    }

    @Test
    void unmappableOrdinalsCountAsNoBlock() {
        SurfaceScene surface = new SurfaceScene(2, 1, new short[]{3, 99}, new short[]{64, 64}, new byte[2], new byte[2]);
        StoredScene stored = new StoredScene(3, Source.FULLSCAN, new ModelInput(ModelKind.FULLSCAN, surface, 0, 0, 1, 2));

        FindingScene scene = SceneView.of(stored, ordinal -> Optional.ofNullable(KEYS.get(ordinal)));

        assertEquals(List.of("minecraft:dirt"), scene.palette());
        assertArrayEquals(new int[]{0, -1}, scene.blocks());
    }

    @Test
    void windowIsClampedIntoTheScene() {
        StoredScene stored = new StoredScene(4, Source.MASK, new ModelInput(ModelKind.MASK,
                SurfaceScene.maskOnly(2, 2, new byte[4]), 0, 0, 64, 64));

        FindingScene scene = SceneView.of(stored, ordinal -> Optional.empty());

        assertEquals(1, scene.windowBottom());
        assertEquals(1, scene.windowRight());
    }
}
