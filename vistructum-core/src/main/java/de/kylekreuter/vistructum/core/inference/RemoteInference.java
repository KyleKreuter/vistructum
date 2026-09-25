package de.kylekreuter.vistructum.core.inference;

import com.google.gson.JsonObject;
import de.kylekreuter.vistructum.api.InferenceMode;
import de.kylekreuter.vistructum.api.InferenceStatus;
import de.kylekreuter.vistructum.core.sidecar.SidecarClient;
import de.kylekreuter.vistructum.inference.InferResult;
import de.kylekreuter.vistructum.inference.ModelKind;
import de.kylekreuter.vistructum.inference.SurfaceScene;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class RemoteInference implements Inference {

    private final SidecarClient client;

    public RemoteInference(SidecarClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public CompletableFuture<InferResult> infer(ModelKind kind, SurfaceScene scene, JsonObject context) {
        return client.infer(kind, scene, context);
    }

    @Override
    public CompletableFuture<InferenceStatus> status() {
        return client.versions()
                .thenApply(versions -> versions.isEmpty()
                        ? InferenceStatus.unavailable(InferenceMode.REMOTE, "sidecar has no model loaded")
                        : new InferenceStatus(InferenceMode.REMOTE, true, versions, Optional.empty()))
                .exceptionally(error -> InferenceStatus.unavailable(InferenceMode.REMOTE, rootMessage(error)));
    }

    @Override
    public void close() {
        client.close();
    }

    static Throwable root(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }

    static String rootMessage(Throwable error) {
        Throwable cause = root(error);
        return Objects.requireNonNullElse(cause.getMessage(), cause.getClass().getSimpleName());
    }
}
