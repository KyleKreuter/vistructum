package de.kylekreuter.vistructum.api;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record SidecarStatus(boolean reachable, String status, Map<String, Boolean> models, Optional<String> error) {

    public SidecarStatus {
        Objects.requireNonNull(status, "status");
        models = Map.copyOf(models);
        Objects.requireNonNull(error, "error");
    }

    public static SidecarStatus unreachable(String error) {
        return new SidecarStatus(false, "unreachable", Map.of(), Optional.of(error));
    }
}
