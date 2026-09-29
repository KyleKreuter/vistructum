package de.kylekreuter.vistructum.core.inference;

import de.kylekreuter.vistructum.inference.InferenceEngine;
import de.kylekreuter.vistructum.inference.ModelKind;
import de.kylekreuter.vistructum.inference.OcclusionMap;
import de.kylekreuter.vistructum.inference.SurfaceScene;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class OcclusionEngine implements AutoCloseable {

    private final InferenceEngine shared;
    private final Supplier<InferenceEngine> starter;
    private InferenceEngine started;

    private OcclusionEngine(InferenceEngine shared, Supplier<InferenceEngine> starter) {
        this.shared = shared;
        this.starter = starter;
    }

    public static OcclusionEngine sharing(InferenceEngine engine) {
        return new OcclusionEngine(Objects.requireNonNull(engine, "engine"), null);
    }

    public static OcclusionEngine startingOnDemand(Supplier<InferenceEngine> starter) {
        return new OcclusionEngine(null, Objects.requireNonNull(starter, "starter"));
    }

    public CompletableFuture<OcclusionMap> occlusion(ModelKind kind, SurfaceScene scene, int top, int left, int bottom,
                                                     int right) {
        return engine().occlusion(kind, scene, top, left, bottom, right);
    }

    @Override
    public synchronized void close() {
        if (started != null) {
            started.close();
            started = null;
        }
    }

    private synchronized InferenceEngine engine() {
        if (shared != null) {
            return shared;
        }
        if (started == null) {
            started = starter.get();
        }
        return started;
    }
}
