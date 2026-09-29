package de.kylekreuter.vistructum.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregated finding and review counts for a time range.
 *
 * @param days counts per day and detection path for findings created in the range, in ascending day order; days
 *             without findings are missing; the list is unmodifiable
 * @param reviewers verdict counts per reviewer for verdicts recorded in the range, ordered by total count
 *                  descending; the list is unmodifiable
 * @param open number of findings without a verdict, regardless of the range
 * @param oldestOpen creation time of the oldest finding without a verdict, or an empty {@link Optional} if there
 *                   is none
 */
public record FindingStats(List<DailyStats> days, List<ReviewerStats> reviewers, long open,
                           Optional<Instant> oldestOpen) {

    /**
     * Validates the components and copies the lists.
     *
     * @throws NullPointerException if a component or a list entry is {@code null}
     */
    public FindingStats {
        days = List.copyOf(days);
        reviewers = List.copyOf(reviewers);
        Objects.requireNonNull(oldestOpen, "oldestOpen");
    }
}
