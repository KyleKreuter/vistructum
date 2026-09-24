package de.kylekreuter.vistructum.core.sidecar;

import java.util.Map;

/** parsed {@code /health} response; a 503 status still carries a valid body, it just means {@code status != "ok"}. */
public record Health(String status, Map<String, Boolean> models) {
}
