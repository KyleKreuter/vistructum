package de.kylekreuter.vistructum.api;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Health of the inference sidecar as observed by the core plugin.
 *
 * @param reachable {@code true} if the sidecar answered its health request with a success status
 * @param status health status reported by the sidecar, or {@code "unreachable"} if {@code reachable} is
 *               {@code false}
 * @param models unmodifiable map from model kind, see {@link Source#modelKind()}, to whether that model is loaded;
 *               empty if {@code reachable} is {@code false}
 * @param error description of the failure if {@code reachable} is {@code false}; empty otherwise
 */
public record SidecarStatus(boolean reachable, String status, Map<String, Boolean> models, Optional<String> error) {

    /**
     * Validates the components and copies {@code models} into an unmodifiable map.
     *
     * @throws NullPointerException if any reference component is {@code null} or {@code models} contains a
     *                              {@code null} key or value
     */
    public SidecarStatus {
        Objects.requireNonNull(status, "status");
        models = Map.copyOf(models);
        Objects.requireNonNull(error, "error");
    }

    /**
     * Creates the status of a sidecar that could not be reached or answered with an error.
     *
     * @param error description of the failure
     * @return a status with {@code reachable} set to {@code false}, status {@code "unreachable"} and no models
     * @throws NullPointerException if {@code error} is {@code null}
     */
    public static SidecarStatus unreachable(String error) {
        return new SidecarStatus(false, "unreachable", Map.of(), Optional.of(error));
    }
}
