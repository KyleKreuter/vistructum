package de.kylekreuter.vistructum.api;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Finding counts of one detection path on one day.
 *
 * @param day calendar day in the time zone of the server
 * @param source detection path
 * @param created number of findings created on that day
 * @param confirmed number of those findings that carry the verdict {@link Verdict#CONFIRMED}
 * @param falseAlarms number of those findings that carry the verdict {@link Verdict#FALSE_ALARM}
 */
public record DailyStats(LocalDate day, Source source, int created, int confirmed, int falseAlarms) {

    /**
     * Validates the components.
     *
     * @throws NullPointerException if {@code day} or {@code source} is {@code null}
     */
    public DailyStats {
        Objects.requireNonNull(day, "day");
        Objects.requireNonNull(source, "source");
    }
}
