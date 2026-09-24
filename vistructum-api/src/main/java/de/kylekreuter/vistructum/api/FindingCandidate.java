package de.kylekreuter.vistructum.api;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Detection that has passed the duplicate check but has not been stored yet.
 *
 * <p>Candidates are exposed through {@link FindingCreateEvent}. A candidate carries no identifier and no creation
 * time; both are assigned when it is stored as a {@link Finding}.
 *
 * @param source detection path that produced the candidate
 * @param world name of the world containing the detection
 * @param box block region covered by the detection
 * @param score highest model score among the windows of the detection, in the range {@code 0} to {@code 1}
 * @param votes number of model windows whose score reached the model threshold
 * @param players unmodifiable set of the players whose block changes formed the detected build; empty for
 *                candidates of {@link Source#FULLSCAN}
 * @param detail human-readable description of the detection context for display purposes; its format is not
 *               part of the API contract
 * @param modelVersion version identifier of the model that produced the detection
 * @param preview greyscale preview that is stored together with the finding
 */
public record FindingCandidate(Source source, String world, BlockBox box, double score, int votes, Set<UUID> players,
                           String detail, String modelVersion, Preview preview) {

    /**
     * Validates the components and copies {@code players} into an unmodifiable set.
     *
     * @throws NullPointerException if any reference component is {@code null} or {@code players} contains
     *                              {@code null}
     */
    public FindingCandidate {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(box, "box");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(modelVersion, "modelVersion");
        Objects.requireNonNull(preview, "preview");
        players = Set.copyOf(players);
    }
}
