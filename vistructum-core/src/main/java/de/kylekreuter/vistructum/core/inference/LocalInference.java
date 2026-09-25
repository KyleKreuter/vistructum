package de.kylekreuter.vistructum.core.inference;

import com.google.gson.JsonObject;
import de.kylekreuter.vistructum.api.InferenceMode;
import de.kylekreuter.vistructum.api.InferenceStatus;
import de.kylekreuter.vistructum.inference.InferResult;
import de.kylekreuter.vistructum.inference.InferenceEngine;
import de.kylekreuter.vistructum.inference.ModelKind;
import de.kylekreuter.vistructum.inference.SurfaceScene;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class LocalInference implements Inference {

    private final InferenceEngine engine;

    public LocalInference(InferenceEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    @Override
    public CompletableFuture<InferResult> infer(ModelKind kind, SurfaceScene scene, JsonObject context) {
        return engine.infer(kind, scene);
    }

    @Override
    public CompletableFuture<InferenceStatus> status() {
        return engine.models().thenApply(models -> {
            if (models.isEmpty()) {
                return InferenceStatus.unavailable(InferenceMode.LOCAL, "no model loaded");
            }
            Map<String, String> versions = new LinkedHashMap<>();
            models.forEach((kind, info) -> versions.put(kind.id(), info.version()));
            return new InferenceStatus(InferenceMode.LOCAL, true, versions, Optional.empty());
        });
    }

    @Override
    public void close() {
        engine.close();
    }
}
