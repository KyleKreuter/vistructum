package de.kylekreuter.vistructum.api;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * State of the inference path that currently serves detections, as observed by the core plugin.
 *
 * @param mode place where inference currently runs; {@link InferenceMode#LOCAL} while a configured local fallback
 *             replaces an unreachable sidecar
 * @param available {@code true} if at least one model is loaded and answers requests
 * @param models unmodifiable map from model kind, see {@link Source#modelKind()}, to the version of the loaded model;
 *               empty if {@code available} is {@code false}
 * @param error description of the failure if {@code available} is {@code false}, or of the sidecar failure that
 *              activated the local fallback; empty otherwise
 */
public record InferenceStatus(InferenceMode mode, boolean available, Map<String, String> models,
                              Optional<String> error) {

    /**
     * Validates the components and copies {@code models} into an unmodifiable map.
     *
     * @throws NullPointerException if any reference component is {@code null} or {@code models} contains a
     *                              {@code null} key or value
     */
    public InferenceStatus {
        Objects.requireNonNull(mode, "mode");
        models = Map.copyOf(models);
        Objects.requireNonNull(error, "error");
    }

    /**
     * Creates the status of an inference path that cannot serve requests.
     *
     * @param mode place where inference was expected to run
     * @param error description of the failure
     * @return a status with {@code available} set to {@code false} and no models
     * @throws NullPointerException if {@code mode} or {@code error} is {@code null}
     */
    public static InferenceStatus unavailable(InferenceMode mode, String error) {
        return new InferenceStatus(mode, false, Map.of(), Optional.of(error));
    }
}
