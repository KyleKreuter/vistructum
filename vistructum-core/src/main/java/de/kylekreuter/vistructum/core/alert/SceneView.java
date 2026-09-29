package de.kylekreuter.vistructum.core.alert;

import de.kylekreuter.vistructum.api.FindingScene;
import de.kylekreuter.vistructum.inference.ModelKind;
import de.kylekreuter.vistructum.inference.SurfaceScene;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntFunction;

public final class SceneView {

    static final String CHANGED_BLOCK = "minecraft:stone";
    static final int CHANGED_LUMINANCE = 255;

    private SceneView() {
    }

    public static FindingScene of(StoredScene stored, IntFunction<Optional<String>> materialKey) {
        ModelInput input = stored.input();
        SurfaceScene scene = input.scene();
        int count = scene.width() * scene.height();
        List<String> palette = new ArrayList<>();
        int[] blocks = new int[count];
        int[] heights = new int[count];
        int[] luminance = new int[count];
        if (input.kind() == ModelKind.MASK) {
            for (int i = 0; i < count; i++) {
                boolean changed = scene.modified()[i] != 0;
                blocks[i] = changed ? 0 : -1;
                heights[i] = changed ? 1 : 0;
                luminance[i] = changed ? CHANGED_LUMINANCE : 0;
            }
            palette.add(CHANGED_BLOCK);
        } else {
            Map<String, Integer> indices = new HashMap<>();
            int lowest = lowestKnown(scene);
            for (int i = 0; i < count; i++) {
                short block = scene.blocks()[i];
                Optional<String> key = block == SurfaceScene.UNKNOWN ? Optional.empty() : materialKey.apply(block);
                blocks[i] = key.map(k -> indices.computeIfAbsent(k, added -> {
                    palette.add(added);
                    return palette.size() - 1;
                })).orElse(-1);
                heights[i] = block == SurfaceScene.UNKNOWN ? 0 : scene.heights()[i] - lowest;
                luminance[i] = scene.luminance()[i] & 0xFF;
            }
        }
        return new FindingScene(stored.source(), scene.width(), scene.height(), clamp(input.top(), scene.height()),
                clamp(input.left(), scene.width()), clamp(input.bottom() - 1, scene.height()),
                clamp(input.right() - 1, scene.width()), palette, blocks, heights, luminance);
    }

    private static int lowestKnown(SurfaceScene scene) {
        int lowest = Integer.MAX_VALUE;
        for (int i = 0; i < scene.blocks().length; i++) {
            if (scene.blocks()[i] != SurfaceScene.UNKNOWN) {
                lowest = Math.min(lowest, scene.heights()[i]);
            }
        }
        return lowest == Integer.MAX_VALUE ? 0 : lowest;
    }

    private static int clamp(int value, int length) {
        return Math.max(0, Math.min(length - 1, value));
    }
}
