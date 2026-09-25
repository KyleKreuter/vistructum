package de.kylekreuter.vistructum.ui.gui;

import de.kylekreuter.vistructum.api.FindingQuery;
import de.kylekreuter.vistructum.api.ReviewState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record ListPosition(ReviewState state, List<Long> cursors) {

    public ListPosition {
        Objects.requireNonNull(state, "state");
        cursors = List.copyOf(cursors);
    }

    public static ListPosition first(ReviewState state) {
        return new ListPosition(state, List.of());
    }

    int page() {
        return cursors.size() + 1;
    }

    FindingQuery query(int limit) {
        FindingQuery query = FindingQuery.all().state(state).limit(limit);
        return cursors.isEmpty() ? query : query.before(cursors.getLast());
    }

    ListPosition next(long lastId) {
        List<Long> next = new ArrayList<>(cursors);
        next.add(lastId);
        return new ListPosition(state, next);
    }

    ListPosition previous() {
        return new ListPosition(state, cursors.subList(0, Math.max(0, cursors.size() - 1)));
    }

    ListPosition toggled() {
        return first(state == ReviewState.OPEN ? ReviewState.REVIEWED : ReviewState.OPEN);
    }
}
