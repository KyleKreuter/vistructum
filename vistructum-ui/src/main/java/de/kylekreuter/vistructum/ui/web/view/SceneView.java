package de.kylekreuter.vistructum.ui.web.view;

import de.kylekreuter.vistructum.api.FindingScene;

import java.util.List;

public record SceneView(String source, long width, long height, WindowView window, List<String> palette, int[] blocks,
                        int[] heights, int[] luminance) {

    public static SceneView of(FindingScene scene) {
        return new SceneView(FindingView.source(scene.source()), scene.width(), scene.height(),
                new WindowView(scene.windowTop(), scene.windowLeft(), scene.windowBottom(), scene.windowRight()),
                scene.palette(), scene.blocks(), scene.heights(), scene.luminance());
    }

    public record WindowView(long top, long left, long bottom, long right) {
    }
}
