package de.kylekreuter.vistructum.ui.web.view;

import de.kylekreuter.vistructum.api.Heatmap;

public record HeatmapView(long width, long height, int[] values) {

    public static HeatmapView of(Heatmap heatmap) {
        return new HeatmapView(heatmap.width(), heatmap.height(), heatmap.values());
    }
}
