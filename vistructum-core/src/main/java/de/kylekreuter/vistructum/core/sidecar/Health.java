package de.kylekreuter.vistructum.core.sidecar;

import java.util.Map;

public record Health(String status, Map<String, Boolean> models) {
}
