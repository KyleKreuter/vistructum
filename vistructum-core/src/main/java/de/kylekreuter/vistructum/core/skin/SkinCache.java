package de.kylekreuter.vistructum.core.skin;

import de.kylekreuter.vistructum.api.PlayerFace;
import de.kylekreuter.vistructum.api.PlayerSkin;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SkinCache {

    static final Duration SKIN_TTL = Duration.ofHours(24);
    static final Duration MISSING_TTL = Duration.ofHours(1);

    private final SkinStore store;
    private final SkinSource source;
    private final Clock clock;

    public SkinCache(SkinStore store, SkinSource source, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.source = Objects.requireNonNull(source, "source");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CompletableFuture<PlayerFace> face(UUID player, Optional<String> knownName) {
        return lookup(player, knownName).thenApply(lookup -> new PlayerFace(player, lookup.name(),
                lookup.skin().map(skin -> SkinImages.face(skin.png())).orElse(List.of())));
    }

    public CompletableFuture<Optional<PlayerSkin>> skin(UUID player, Optional<String> knownName) {
        return lookup(player, knownName).thenApply(SkinLookup::skin);
    }

    public CompletableFuture<SkinLookup> lookup(UUID player, Optional<String> knownName) {
        return store.find(player).thenCompose(stored -> {
            Instant now = clock.instant();
            if (stored.isPresent() && fresh(stored.get(), now)) {
                return CompletableFuture.completedFuture(stored.get().lookup());
            }
            return source.fetch(player, knownName)
                    .thenCompose(fetched -> store.save(fetched, now).thenApply(saved -> fetched))
                    .exceptionally(error -> stored.map(StoredSkin::lookup)
                            .orElseGet(() -> SkinLookup.without(player, knownName)));
        });
    }

    static boolean fresh(StoredSkin stored, Instant now) {
        Duration ttl = stored.lookup().skin().isPresent() ? SKIN_TTL : MISSING_TTL;
        return stored.fetchedAt().plus(ttl).isAfter(now);
    }
}
