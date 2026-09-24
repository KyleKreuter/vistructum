package de.kylekreuter.vistructum.api;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Persisted detection of a suspicious build.
 *
 * <p>A finding is an immutable snapshot of a stored record. Its greyscale preview is not part of this snapshot
 * and is loaded separately through {@link Findings#preview(long)}.
 *
 * @param id unique, positive identifier, assigned in ascending order of creation and never reused
 * @param source detection path that produced the finding
 * @param world name of the world containing the detection
 * @param box block region covered by the detection
 * @param score highest model score among the windows of the detection, in the range {@code 0} to {@code 1}
 * @param votes number of model windows whose score reached the model threshold
 * @param players unmodifiable set of the players whose block changes formed the detected build; empty for
 *                findings of {@link Source#FULLSCAN}
 * @param detail human-readable description of the detection context for display purposes; its format is not
 *               part of the API contract
 * @param modelVersion version identifier of the model that produced the detection
 * @param createdAt time at which the finding was stored
 * @param review verdict of the finding, or an empty {@link Optional} while the finding is unreviewed
 */
public record Finding(long id, Source source, String world, BlockBox box, double score, int votes, Set<UUID> players,
                      String detail, String modelVersion, Instant createdAt, Optional<Review> review) {

    /**
     * Validates the components and copies {@code players} into an unmodifiable set.
     *
     * @throws NullPointerException if any reference component is {@code null} or {@code players} contains
     *                              {@code null}
     */
    public Finding {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(box, "box");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(modelVersion, "modelVersion");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(review, "review");
        players = Set.copyOf(players);
    }

    /**
     * Reports whether the finding still awaits a verdict.
     *
     * @return {@code true} if {@link #review()} is empty
     */
    public boolean open() {
        return review.isEmpty();
    }
}
