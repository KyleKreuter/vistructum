package de.kylekreuter.vistructum.core.recording;

import de.kylekreuter.vistructum.api.MotionFrame;
import de.kylekreuter.vistructum.core.store.Database;
import de.kylekreuter.vistructum.core.store.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MotionStoreTest {

    private static final UUID PLAYER = UUID.fromString("e3e04125-846a-3ff7-9f1f-aa61ff8eb6b7");

    @TempDir
    Path directory;
    private Database database;
    private MotionStore store;

    @BeforeEach
    void open() {
        database = TestDatabase.open(directory);
        store = new MotionStore(database);
    }

    @AfterEach
    void close() {
        database.close();
    }

    @Test
    void appendWritesOneRowPerChunkAndDropsChunksEndedBeforeTheCutoff() throws Exception {
        store.append(List.of(chunk(0), chunk(5_000)), 0).get();
        assertEquals(2, store.count().get());

        store.append(List.of(chunk(10_000)), 5_000).get();

        assertEquals(2, store.count().get());
        store.append(List.of(), 20_000).get();
        assertEquals(0, store.count().get());
    }

    private static MotionChunk chunk(long start) {
        return new MotionChunk(PLAYER, "Builder", "world", List.of(
                new MotionFrame(start, 1, 64, 1, 0f, 0f, MotionFrame.ON_GROUND, "minecraft:air"),
                new MotionFrame(start + 4_950, 2, 64, 1, 0f, 0f, MotionFrame.ON_GROUND, "minecraft:air")));
    }
}
