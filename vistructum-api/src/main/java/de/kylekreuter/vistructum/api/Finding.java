package de.kylekreuter.vistructum.api;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record Finding(long id, Source source, String world, BlockBox box, double score, int votes, Set<UUID> players,
                      String detail, String modelVersion, Instant createdAt, Optional<Review> review) {

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

    public boolean open() {
        return review.isEmpty();
    }
}
