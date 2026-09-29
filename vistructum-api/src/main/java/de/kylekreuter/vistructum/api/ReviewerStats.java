package de.kylekreuter.vistructum.api;

import java.util.Objects;

/**
 * Verdict counts of one reviewer.
 *
 * @param reviewer name of the reviewer as stored with the verdicts
 * @param confirmed number of findings the reviewer confirmed
 * @param falseAlarms number of findings the reviewer rejected as false alarms
 */
public record ReviewerStats(String reviewer, int confirmed, int falseAlarms) {

    /**
     * Validates the components.
     *
     * @throws NullPointerException if {@code reviewer} is {@code null}
     */
    public ReviewerStats {
        Objects.requireNonNull(reviewer, "reviewer");
    }
}
