package de.kylekreuter.vistructum.core.inference;

import com.google.gson.JsonObject;
import de.kylekreuter.vistructum.api.InferenceStatus;
import de.kylekreuter.vistructum.inference.InferResult;
import de.kylekreuter.vistructum.inference.ModelKind;
import de.kylekreuter.vistructum.inference.SurfaceScene;

import java.util.concurrent.CompletableFuture;

public interface Inference extends AutoCloseable {

    CompletableFuture<InferResult> infer(ModelKind kind, SurfaceScene scene, JsonObject context);

    CompletableFuture<InferenceStatus> status();

    @Override
    void close();
}
