package de.kylekreuter.vistructum.core.skin;

import de.kylekreuter.vistructum.api.PlayerFace;
import de.kylekreuter.vistructum.api.PlayerSkin;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinCacheTest {

    private static final Instant NOW = Instant.parse("2026-09-25T18:00:00Z");
    private static final UUID PLAYER = UUID.fromString("e3e04125-846a-3ff7-9f1f-aa61ff8eb6b7");
    private static final byte[] SKIN = SkinImagesTest.skin(64, 0xFFC39F85);

    @TempDir
    Path directory;
    private Database database;
    private SkinStore store;

    @BeforeEach
    void open() {
        database = TestDatabase.open(directory);
        store = new SkinStore(database);
    }

    @AfterEach
    void close() {
        database.close();
    }

    @Test
    void storedSkinRoundTrips() throws Exception {
        store.save(SkinLookup.of(PLAYER, Optional.of("kyleonaut"), SKIN, true), NOW).get();
        StoredSkin stored = store.find(PLAYER).get().orElseThrow();
        assertEquals(new PlayerSkin(PLAYER, Optional.of("kyleonaut"), SKIN, true), stored.lookup().skin().orElseThrow());
        assertEquals(NOW, stored.fetchedAt());
        store.save(SkinLookup.without(PLAYER, Optional.empty()), NOW).get();
        assertTrue(store.find(PLAYER).get().orElseThrow().lookup().skin().isEmpty());
    }

    @Test
    void faceIsDerivedFromTheStoredSkin() throws Exception {
        SkinCache cache = cache(NOW, counting(new AtomicInteger(), true));
        PlayerFace face = cache.face(PLAYER, Optional.of("kyleonaut")).get();
        PlayerSkin skin = cache.skin(PLAYER, Optional.of("kyleonaut")).get().orElseThrow();

        assertEquals(64, face.pixels().size());
        assertEquals(0xC39F85, face.pixels().getFirst());
        assertArrayEquals(SKIN, skin.png());
        assertTrue(skin.slim());
    }

    @Test
    void freshSkinIsServedWithoutFetching() throws Exception {
        AtomicInteger fetches = new AtomicInteger();
        SkinCache cache = cache(NOW, counting(fetches, true));
        cache.face(PLAYER, Optional.of("kyleonaut")).get();
        PlayerFace second = cache(NOW.plus(Duration.ofHours(23)), counting(fetches, true))
                .face(PLAYER, Optional.of("kyleonaut")).get();
        assertEquals(1, fetches.get());
        assertTrue(second.hasSkin());
        assertTrue(cache(NOW.plus(Duration.ofHours(23)), counting(fetches, true)).skin(PLAYER, Optional.empty()).get()
                .isPresent());
        assertEquals(1, fetches.get());
    }

    @Test
    void outdatedEntriesAreFetchedAgain() throws Exception {
        AtomicInteger fetches = new AtomicInteger();
        cache(NOW, counting(fetches, true)).face(PLAYER, Optional.empty()).get();
        Instant expired = NOW.plus(SkinCache.SKIN_TTL).plusSeconds(1);
        cache(expired, counting(fetches, false)).face(PLAYER, Optional.empty()).get();
        cache(expired.plus(Duration.ofMinutes(59)), counting(fetches, true)).skin(PLAYER, Optional.empty()).get();
        cache(expired.plus(SkinCache.MISSING_TTL), counting(fetches, true)).face(PLAYER, Optional.empty()).get();
        assertEquals(3, fetches.get());
    }

    @Test
    void failedFetchFallsBackToTheOutdatedSkinAndStoresNothing() throws Exception {
        cache(NOW, counting(new AtomicInteger(), true)).face(PLAYER, Optional.empty()).get();
        SkinSource failing = (player, name) -> CompletableFuture.failedFuture(new IllegalStateException("down"));
        Instant later = NOW.plus(Duration.ofDays(2));
        assertTrue(cache(later, failing).face(PLAYER, Optional.empty()).get().hasSkin());
        assertTrue(cache(later, failing).skin(PLAYER, Optional.empty()).get().isPresent());
        assertEquals(NOW, store.find(PLAYER).get().orElseThrow().fetchedAt());
    }

    @Test
    void failedFetchWithoutStoredSkinHasNoSkin() throws Exception {
        SkinSource failing = (player, name) -> CompletableFuture.failedFuture(new IllegalStateException("down"));
        PlayerFace face = cache(NOW, failing).face(PLAYER, Optional.of("kyleonaut")).get();
        assertFalse(face.hasSkin());
        assertEquals(Optional.of("kyleonaut"), face.name());
        assertTrue(cache(NOW, failing).skin(PLAYER, Optional.empty()).get().isEmpty());
        assertTrue(store.find(PLAYER).get().isEmpty());
    }

    private SkinCache cache(Instant now, SkinSource source) {
        return new SkinCache(store, source, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static SkinSource counting(AtomicInteger fetches, boolean withSkin) {
        return (player, name) -> {
            fetches.incrementAndGet();
            return CompletableFuture.completedFuture(withSkin ? SkinLookup.of(player, name, SKIN, true)
                    : SkinLookup.without(player, name));
        };
    }
}
