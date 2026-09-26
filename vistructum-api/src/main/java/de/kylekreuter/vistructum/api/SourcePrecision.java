package de.kylekreuter.vistructum.api;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Review outcome of all findings of one detection path, returned by {@link Findings#precision()}.
 *
 * <p>Only findings with a verdict are counted. Findings without a verdict and findings removed by retention do not
 * contribute.
 *
 * @param source detection path the counts refer to
 * @param confirmed number of findings of this path with verdict {@link Verdict#CONFIRMED}
 * @param falseAlarms number of findings of this path with verdict {@link Verdict#FALSE_ALARM}
 */
public record SourcePrecision(Source source, long confirmed, long falseAlarms) {

    /**
     * Validates the components.
     *
     * @throws NullPointerException if {@code source} is {@code null}
     * @throws IllegalArgumentException if a count is negative
     */
    public SourcePrecision {
        Objects.requireNonNull(source, "source");
        if (confirmed < 0 || falseAlarms < 0) {
            throw new IllegalArgumentException("negative count " + confirmed + "/" + falseAlarms);
        }
    }

    /**
     * Returns the number of reviewed findings of this path.
     *
     * @return the sum of {@link #confirmed()} and {@link #falseAlarms()}
     */
    public long reviewed() {
        return confirmed + falseAlarms;
    }

    /**
     * Returns the share of confirmed findings among the reviewed findings of this path.
     *
     * @return {@link #confirmed()} divided by {@link #reviewed()}, in the range {@code 0} to {@code 1}, or an empty
     *         value if no finding of this path has been reviewed
     */
    public OptionalDouble precision() {
        long reviewed = reviewed();
        return reviewed == 0 ? OptionalDouble.empty() : OptionalDouble.of((double) confirmed / reviewed);
    }
}
