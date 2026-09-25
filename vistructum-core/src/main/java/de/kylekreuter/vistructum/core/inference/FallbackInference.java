package de.kylekreuter.vistructum.core.inference;

import com.google.gson.JsonObject;
import de.kylekreuter.vistructum.api.InferenceMode;
import de.kylekreuter.vistructum.api.InferenceStatus;
import de.kylekreuter.vistructum.core.sidecar.SidecarException;
import de.kylekreuter.vistructum.inference.InferResult;
import de.kylekreuter.vistructum.inference.ModelKind;
import de.kylekreuter.vistructum.inference.SurfaceScene;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public final class FallbackInference implements Inference {

    private static final long WARN_INTERVAL_MILLIS = 60_000;

    private final Inference remote;
    private final Inference local;
    private final Logger logger;
    private final AtomicLong lastWarning = new AtomicLong();

    public FallbackInference(Inference remote, Inference local, Logger logger) {
        this.remote = Objects.requireNonNull(remote, "remote");
        this.local = Objects.requireNonNull(local, "local");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public CompletableFuture<InferResult> infer(ModelKind kind, SurfaceScene scene, JsonObject context) {
        return remote.infer(kind, scene, context).exceptionallyCompose(error -> {
            if (!sidecarDown(RemoteInference.root(error))) {
                return CompletableFuture.failedFuture(error);
            }
            warn(RemoteInference.rootMessage(error));
            return local.infer(kind, scene, context);
        });
    }

    @Override
    public CompletableFuture<InferenceStatus> status() {
        return remote.status().thenCompose(status -> status.available() ? CompletableFuture.completedFuture(status)
                : local.status().thenApply(fallback -> new InferenceStatus(InferenceMode.LOCAL, fallback.available(),
                fallback.models(), Optional.of("sidecar: " + status.error().orElse("unavailable")))));
    }

    @Override
    public void close() {
        remote.close();
        local.close();
    }

    static boolean sidecarDown(Throwable cause) {
        return cause instanceof IOException
                || cause instanceof SidecarException sidecar && sidecar.status() >= 500;
    }

    private void warn(String reason) {
        long now = System.currentTimeMillis();
        long last = lastWarning.get();
        if (now - last > WARN_INTERVAL_MILLIS && lastWarning.compareAndSet(last, now)) {
            logger.warning("sidecar unavailable, local inference takes over: " + reason);
        }
    }
}
