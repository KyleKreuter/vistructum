package de.kylekreuter.vistructum.api;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public record FindingQuery(Optional<String> world, Optional<Source> source, ReviewState state, Optional<Instant> since,
                           OptionalLong beforeId, int limit) {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 500;

    public FindingQuery {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(since, "since");
        Objects.requireNonNull(beforeId, "beforeId");
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be in 1.." + MAX_LIMIT + ", got " + limit);
        }
    }

    public static FindingQuery all() {
        return new FindingQuery(Optional.empty(), Optional.empty(), ReviewState.ANY, Optional.empty(),
                OptionalLong.empty(), DEFAULT_LIMIT);
    }

    public static FindingQuery open() {
        return all().state(ReviewState.OPEN);
    }

    public FindingQuery world(String world) {
        return new FindingQuery(Optional.of(world), source, state, since, beforeId, limit);
    }

    public FindingQuery source(Source source) {
        return new FindingQuery(world, Optional.of(source), state, since, beforeId, limit);
    }

    public FindingQuery state(ReviewState state) {
        return new FindingQuery(world, source, state, since, beforeId, limit);
    }

    public FindingQuery since(Instant since) {
        return new FindingQuery(world, source, state, Optional.of(since), beforeId, limit);
    }

    public FindingQuery before(long id) {
        return new FindingQuery(world, source, state, since, OptionalLong.of(id), limit);
    }

    public FindingQuery limit(int limit) {
        return new FindingQuery(world, source, state, since, beforeId, limit);
    }
}
