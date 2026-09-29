package de.kylekreuter.vistructum.ui.web.view;

import de.kylekreuter.vistructum.api.FindingStats;
import de.kylekreuter.vistructum.api.SourcePrecision;

import java.time.Instant;
import java.util.List;

public record StatsView(List<DayView> days, List<ReviewerView> reviewers, long open, String oldestOpen,
                        List<PrecisionView> precision) {

    public static StatsView of(FindingStats stats, List<SourcePrecision> precision) {
        return new StatsView(
                stats.days().stream().map(day -> new DayView(day.day().toString(), FindingView.source(day.source()),
                        day.created(), day.confirmed(), day.falseAlarms())).toList(),
                stats.reviewers().stream().map(reviewer -> new ReviewerView(reviewer.reviewer(), reviewer.confirmed(),
                        reviewer.falseAlarms())).toList(),
                stats.open(), stats.oldestOpen().map(Instant::toString).orElse(null),
                precision.stream().map(source -> new PrecisionView(FindingView.source(source.source()),
                        source.confirmed(), source.falseAlarms())).toList());
    }

    public record DayView(String day, String source, long created, long confirmed, long falseAlarms) {
    }

    public record ReviewerView(String reviewer, long confirmed, long falseAlarms) {
    }

    public record PrecisionView(String source, long confirmed, long falseAlarms) {
    }
}
