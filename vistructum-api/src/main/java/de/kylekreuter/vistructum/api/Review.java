package de.kylekreuter.vistructum.api;

import java.time.Instant;
import java.util.Objects;

/**
 * Verdict recorded for a finding.
 *
 * @param verdict assessment of the finding
 * @param reviewer name of the reviewing party as supplied to {@link Findings#review(long, Verdict, String)}
 * @param reviewedAt time at which the verdict was recorded
 */
public record Review(Verdict verdict, String reviewer, Instant reviewedAt) {

    /**
     * Validates the components.
     *
     * @throws NullPointerException if any component is {@code null}
     */
    public Review {
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(reviewer, "reviewer");
        Objects.requireNonNull(reviewedAt, "reviewedAt");
    }
}
