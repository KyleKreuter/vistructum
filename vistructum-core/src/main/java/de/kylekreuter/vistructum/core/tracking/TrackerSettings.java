package de.kylekreuter.vistructum.core.tracking;

import java.time.Duration;
import java.util.Objects;

/**
 * Tuning for {@link ModificationTracker}.
 *
 * @param ttl          changes older than this are forgotten
 * @param linkDistance two changes join the same cluster when their Chebyshev distance is <= this
 * @param quietPeriod  a cluster is ready only after no change inside it for this long
 * @param minBlocks    clusters smaller than this are never reported
 * @param maxExtent    clusters whose bbox exceeds this on any axis are still reported, flagged oversized
 */
public record TrackerSettings(Duration ttl, int linkDistance, Duration quietPeriod, int minBlocks, int maxExtent) {

    public TrackerSettings {
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

    /** {@code linkDistance=3}, {@code quietPeriod=20s}, {@code minBlocks=12}, {@code maxExtent=256}. */
    public static TrackerSettings defaults(Duration ttl) {
        return new TrackerSettings(ttl, 3, Duration.ofSeconds(20), 12, 256);
    }

    /** {@link #defaults(Duration)} with {@code ttl=10min}, matching {@code config.yml}'s {@code tracking.ttl-minutes}. */
    public static TrackerSettings defaults() {
        return defaults(Duration.ofMinutes(10));
    }
}
