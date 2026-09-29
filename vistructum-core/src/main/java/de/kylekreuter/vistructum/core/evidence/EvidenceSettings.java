package de.kylekreuter.vistructum.core.evidence;

import java.time.Duration;
import java.util.Objects;

public record EvidenceSettings(int radius, Duration lead, int margin) {

    public EvidenceSettings {
        Objects.requireNonNull(lead, "lead");
        if (radius < 0) {
            throw new IllegalArgumentException("radius must be >= 0");
        }
        if (lead.isNegative()) {
            throw new IllegalArgumentException("lead must not be negative");
        }
        if (margin < 0) {
            throw new IllegalArgumentException("margin must be >= 0");
        }
    }
}
