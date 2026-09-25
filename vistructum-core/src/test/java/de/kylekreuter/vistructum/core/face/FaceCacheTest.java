package de.kylekreuter.vistructum.core.face;

import de.kylekreuter.vistructum.api.PlayerFace;
import de.kylekreuter.vistructum.core.store.Database;
import de.kylekreuter.vistructum.core.store.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaceCacheTest {

    private static final Instant NOW = Instant.parse("2026-09-25T18:00:00Z");
    private static final UUID PLAYER = UUID.fromString("e3e04125-846a-3ff7-9f1f-aa61ff8eb6b7");
    private static final List<Integer> SKIN = Collections.nCopies(64, 0xC39F85);

    @TempDir
    Path directory;
    private Database database;
    private FaceStore store;

    @BeforeEach
    void open() {
        database = TestDatabase.open(directory);
        store = new FaceStore(database);
    }

    @AfterEach
    void close() {
        database.close();
    }

    @Test
    void storedFaceRoundTrips() throws Exception {
        store.save(new PlayerFace(PLAYER, Optional.of("kyleonaut"), SKIN), NOW).get();
        StoredFace stored = store.find(PLAYER).get().orElseThrow();
        assertEquals(new PlayerFace(PLAYER, Optional.of("kyleonaut"), SKIN), stored.face());
        assertEquals(NOW, stored.fetchedAt());
        store.save(new PlayerFace(PLAYER, Optional.empty(), List.of()), NOW).get();
        assertFalse(store.find(PLAYER).get().orElseThrow().face().hasSkin());
    }

    @Test
    void freshSkinIsServedWithoutFetching() throws Exception {
        AtomicInteger fetches = new AtomicInteger();
        FaceCache cache = cache(NOW, counting(fetches, SKIN));
        cache.face(PLAYER, Optional.of("kyleonaut")).get();
        PlayerFace second = cache(NOW.plus(Duration.ofHours(23)), counting(fetches, SKIN))
                .face(PLAYER, Optional.of("kyleonaut")).get();
        assertEquals(1, fetches.get());
        assertTrue(second.hasSkin());
    }

    @Test
    void outdatedEntriesAreFetchedAgain() throws Exception {
        AtomicInteger fetches = new AtomicInteger();
        cache(NOW, counting(fetches, SKIN)).face(PLAYER, Optional.empty()).get();
        Instant expired = NOW.plus(FaceCache.SKIN_TTL).plusSeconds(1);
        cache(expired, counting(fetches, List.of())).face(PLAYER, Optional.empty()).get();
        cache(expired.plus(Duration.ofMinutes(59)), counting(fetches, SKIN)).face(PLAYER, Optional.empty()).get();
        cache(expired.plus(FaceCache.MISSING_TTL), counting(fetches, SKIN)).face(PLAYER, Optional.empty()).get();
        assertEquals(3, fetches.get());
    }

    @Test
    void failedFetchFallsBackToTheOutdatedFaceAndStoresNothing() throws Exception {
        cache(NOW, counting(new AtomicInteger(), SKIN)).face(PLAYER, Optional.empty()).get();
        FaceSource failing = (player, name) -> CompletableFuture.failedFuture(new IllegalStateException("down"));
        Instant later = NOW.plus(Duration.ofDays(2));
        assertTrue(cache(later, failing).face(PLAYER, Optional.empty()).get().hasSkin());
        assertEquals(NOW, store.find(PLAYER).get().orElseThrow().fetchedAt());
    }

    @Test
    void failedFetchWithoutStoredFaceHasNoSkin() throws Exception {
        FaceSource failing = (player, name) -> CompletableFuture.failedFuture(new IllegalStateException("down"));
        PlayerFace face = cache(NOW, failing).face(PLAYER, Optional.of("kyleonaut")).get();
        assertFalse(face.hasSkin());
        assertEquals(Optional.of("kyleonaut"), face.name());
        assertTrue(store.find(PLAYER).get().isEmpty());
    }

    private FaceCache cache(Instant now, FaceSource source) {
        return new FaceCache(store, source, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static FaceSource counting(AtomicInteger fetches, List<Integer> pixels) {
        return (player, name) -> {
            fetches.incrementAndGet();
            return CompletableFuture.completedFuture(new PlayerFace(player, name, pixels));
        };
    }
}
