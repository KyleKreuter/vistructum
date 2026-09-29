package de.kylekreuter.vistructum.core.recording;

import de.kylekreuter.vistructum.api.MotionFrame;
import de.kylekreuter.vistructum.core.store.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class MotionStore {

    private static final String INSERT = """
            INSERT INTO motion_chunks (player, player_name, world, start_ms, end_ms, min_x, min_y, min_z, max_x, max_y,
                max_z, frames)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final Database database;

    public MotionStore(Database database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    public CompletableFuture<Void> append(List<MotionChunk> chunks, long deleteEndedBefore) {
        List<byte[]> encoded = chunks.stream().map(chunk -> MotionCodec.encode(chunk.frames())).toList();
        return database.transaction(connection -> {
            try (PreparedStatement insert = connection.prepareStatement(INSERT)) {
                for (int i = 0; i < chunks.size(); i++) {
                    MotionChunk chunk = chunks.get(i);
                    insert.setString(1, chunk.player().toString());
                    insert.setString(2, chunk.playerName());
                    insert.setString(3, chunk.world());
                    insert.setLong(4, chunk.startMillis());
                    insert.setLong(5, chunk.endMillis());
                    insert.setDouble(6, chunk.frames().stream().mapToDouble(MotionFrame::x).min().orElseThrow());
                    insert.setDouble(7, chunk.frames().stream().mapToDouble(MotionFrame::y).min().orElseThrow());
                    insert.setDouble(8, chunk.frames().stream().mapToDouble(MotionFrame::z).min().orElseThrow());
                    insert.setDouble(9, chunk.frames().stream().mapToDouble(MotionFrame::x).max().orElseThrow());
                    insert.setDouble(10, chunk.frames().stream().mapToDouble(MotionFrame::y).max().orElseThrow());
                    insert.setDouble(11, chunk.frames().stream().mapToDouble(MotionFrame::z).max().orElseThrow());
                    insert.setBytes(12, encoded.get(i));
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM motion_chunks WHERE end_ms < ?")) {
                delete.setLong(1, deleteEndedBefore);
                delete.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Integer> count() {
        return database.transaction(connection -> {
            try (PreparedStatement select = connection.prepareStatement("SELECT count(*) FROM motion_chunks");
                 ResultSet rows = select.executeQuery()) {
                return rows.getInt(1);
            }
        });
    }
}
