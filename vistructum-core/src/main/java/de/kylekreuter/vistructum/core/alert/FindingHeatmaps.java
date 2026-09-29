package de.kylekreuter.vistructum.core.alert;

import de.kylekreuter.vistructum.api.Heatmap;
import de.kylekreuter.vistructum.core.inference.OcclusionEngine;
import de.kylekreuter.vistructum.inference.OcclusionMap;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class FindingHeatmaps {

    private final FindingStore store;
    private final OcclusionEngine engine;

    public FindingHeatmaps(FindingStore store, OcclusionEngine engine) {
        this.store = Objects.requireNonNull(store, "store");
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    public CompletableFuture<Optional<Heatmap>> heatmap(long id) {
        return store.heatmap(id).thenCompose(stored -> stored.isPresent()
                ? CompletableFuture.completedFuture(stored)
                : store.scene(id).thenCompose(scene -> scene.isEmpty()
                        ? CompletableFuture.completedFuture(Optional.<Heatmap>empty())
                        : compute(scene.get())));
    }

    private CompletableFuture<Optional<Heatmap>> compute(StoredScene scene) {
        ModelInput input = scene.input();
        return engine.occlusion(input.kind(), input.scene(), input.top(), input.left(), input.bottom(), input.right())
                .thenCompose(map -> {
                    Heatmap heatmap = toHeatmap(map);
                    return store.storeHeatmap(scene.findingId(), heatmap, map.modelVersion())
                            .thenApply(stored -> stored ? Optional.of(heatmap) : Optional.<Heatmap>empty());
                });
    }

    private static Heatmap toHeatmap(OcclusionMap map) {
        return new Heatmap(map.width(), map.height(), map.values());
    }
}
