package de.kylekreuter.vistructum.core.tracking;

import java.time.Duration;
import java.util.Objects;

public record ClusterSettings(Duration ttl, int linkDistance, Duration quietPeriod, int minBlocks, int maxExtent) {

    public ClusterSettings {
        Objects.requireNonNull(ttl, "ttl");
        Objects.requireNonNull(quietPeriod, "quietPeriod");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        if (quietPeriod.isNegative()) {
            throw new IllegalArgumentException("quietPeriod must not be negative");
        }
        if (linkDistance < 1) {
            throw new IllegalArgumentException("linkDistance must be >= 1");
        }
        if (minBlocks < 1) {
            throw new IllegalArgumentException("minBlocks must be >= 1");
        }
        if (maxExtent < 1) {
            throw new IllegalArgumentException("maxExtent must be >= 1");
        }
    }
}
