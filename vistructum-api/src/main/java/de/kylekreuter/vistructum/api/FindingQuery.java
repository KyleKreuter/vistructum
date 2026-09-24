package de.kylekreuter.vistructum.api;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Immutable selection criteria for {@link Findings#find(FindingQuery)}.
 *
 * <p>All criteria are combined conjunctively. An empty optional criterion does not restrict the selection.
 * Instances are created through {@link #all()} or {@link #open()} and refined with the wither methods, each of
 * which returns a new instance and leaves the receiver unchanged.
 *
 * @param world exact name of the world a finding must belong to
 * @param source detection path a finding must originate from
 * @param state review state a finding must be in
 * @param since earliest creation time a finding may have, inclusive
 * @param beforeId exclusive upper bound for finding identifiers, used as the paging cursor
 * @param limit maximum number of findings per page, between {@code 1} and {@link #MAX_LIMIT} inclusive
 */
public record FindingQuery(Optional<String> world, Optional<Source> source, ReviewState state, Optional<Instant> since,
                           OptionalLong beforeId, int limit) {

    /**
     * Page size applied by {@link #all()} and {@link #open()}.
     */
    public static final int DEFAULT_LIMIT = 20;

    /**
     * Largest permitted page size.
     */
    public static final int MAX_LIMIT = 500;

    /**
     * Validates the criteria.
     *
     * @throws NullPointerException if any component other than {@code limit} is {@code null}
     * @throws IllegalArgumentException if {@code limit} is outside the range {@code 1} to {@link #MAX_LIMIT}
     */
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

    /**
     * Creates a query that selects every finding regardless of review state, with a page size of
     * {@link #DEFAULT_LIMIT}.
     *
     * @return an unrestricted query
     */
    public static FindingQuery all() {
        return new FindingQuery(Optional.empty(), Optional.empty(), ReviewState.ANY, Optional.empty(),
                OptionalLong.empty(), DEFAULT_LIMIT);
    }

    /**
     * Creates a query that selects every finding without a verdict, with a page size of {@link #DEFAULT_LIMIT}.
     *
     * @return a query restricted to {@link ReviewState#OPEN}
     */
    public static FindingQuery open() {
        return all().state(ReviewState.OPEN);
    }

    /**
     * Restricts the selection to one world.
     *
     * @param world exact, case-sensitive world name
     * @return a new query with the world criterion replaced
     * @throws NullPointerException if {@code world} is {@code null}
     */
    public FindingQuery world(String world) {
        return new FindingQuery(Optional.of(world), source, state, since, beforeId, limit);
    }

    /**
     * Restricts the selection to one detection path.
     *
     * @param source detection path
     * @return a new query with the source criterion replaced
     * @throws NullPointerException if {@code source} is {@code null}
     */
    public FindingQuery source(Source source) {
        return new FindingQuery(world, Optional.of(source), state, since, beforeId, limit);
    }

    /**
     * Restricts the selection to one review state.
     *
     * @param state review state; {@link ReviewState#ANY} removes the restriction
     * @return a new query with the state criterion replaced
     * @throws NullPointerException if {@code state} is {@code null}
     */
    public FindingQuery state(ReviewState state) {
        return new FindingQuery(world, source, state, since, beforeId, limit);
    }

    /**
     * Restricts the selection to findings created at or after the given instant.
     *
     * @param since earliest creation time, inclusive
     * @return a new query with the time criterion replaced
     * @throws NullPointerException if {@code since} is {@code null}
     */
    public FindingQuery since(Instant since) {
        return new FindingQuery(world, source, state, Optional.of(since), beforeId, limit);
    }

    /**
     * Restricts the selection to findings with an identifier strictly below the given value.
     *
     * <p>This criterion is the paging cursor applied by {@link Page#next()}. Setting it explicitly resumes a
     * selection below a known identifier.
     *
     * @param id exclusive upper bound for identifiers
     * @return a new query with the cursor replaced
     */
    public FindingQuery before(long id) {
        return new FindingQuery(world, source, state, since, OptionalLong.of(id), limit);
    }

    /**
     * Sets the page size.
     *
     * @param limit maximum number of findings per page
     * @return a new query with the page size replaced
     * @throws IllegalArgumentException if {@code limit} is outside the range {@code 1} to {@link #MAX_LIMIT}
     */
    public FindingQuery limit(int limit) {
        return new FindingQuery(world, source, state, since, beforeId, limit);
    }
}
