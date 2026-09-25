package de.kylekreuter.vistructum.core.face;

import de.kylekreuter.vistructum.api.PlayerFace;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class FaceCache {

    static final Duration SKIN_TTL = Duration.ofHours(24);
    static final Duration MISSING_TTL = Duration.ofHours(1);

    private final FaceStore store;
    private final FaceSource source;
    private final Clock clock;

    public FaceCache(FaceStore store, FaceSource source, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.source = Objects.requireNonNull(source, "source");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CompletableFuture<PlayerFace> face(UUID player, Optional<String> knownName) {
        return store.find(player).thenCompose(stored -> {
            Instant now = clock.instant();
            if (stored.isPresent() && fresh(stored.get(), now)) {
                return CompletableFuture.completedFuture(stored.get().face());
            }
            return source.fetch(player, knownName)
                    .thenCompose(fetched -> store.save(fetched, now).thenApply(saved -> fetched))
                    .exceptionally(error -> stored.map(StoredFace::face)
                            .orElseGet(() -> new PlayerFace(player, knownName, List.of())));
        });
    }

    static boolean fresh(StoredFace stored, Instant now) {
        Duration ttl = stored.face().hasSkin() ? SKIN_TTL : MISSING_TTL;
        return stored.fetchedAt().plus(ttl).isAfter(now);
    }
}
