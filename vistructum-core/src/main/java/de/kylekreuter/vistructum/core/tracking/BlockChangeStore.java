package de.kylekreuter.vistructum.core.tracking;

import de.kylekreuter.vistructum.core.scene.BlockPos;
import de.kylekreuter.vistructum.core.store.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BlockChangeStore {

    private static final String UPSERT = """
            INSERT INTO block_changes (world, x, y, z, player, kind, material, changed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (world, x, y, z, player) DO UPDATE
            SET kind = excluded.kind, material = excluded.material, changed_at = excluded.changed_at
            """;
    private static final String MARK_REPORTED =
            "UPDATE block_changes SET reported_at = ? WHERE world = ? AND x = ? AND y = ? AND z = ?";

    private final Database database;
    private final Queue<BlockChange> unwritten = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean flushQueued = new AtomicBoolean();

    public BlockChangeStore(Database database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    public void record(String world, BlockPos pos, UUID player, ChangeKind kind, String material, long changedAt) {
        unwritten.add(new BlockChange(world, pos, player, kind, material, changedAt, 0));
        if (flushQueued.compareAndSet(false, true)) {
            database.transaction(this::flush);
        }
    }

    public CompletableFuture<List<Cluster>> takeReady(ClusterSettings settings, long nowMillis) {
        return database.transaction(connection -> {
            flush(connection);
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM block_changes WHERE changed_at < ?")) {
                delete.setLong(1, nowMillis - settings.ttl().toMillis());
                delete.executeUpdate();
            }
            if (!anyUnreported(connection)) {
                return List.of();
            }
            List<Cluster> ready = Clustering.ready(readAll(connection), settings, nowMillis);
            markReported(connection, ready, nowMillis);
            return ready;
        });
    }

    public CompletableFuture<Integer> count() {
        return database.transaction(connection -> {
            try (PreparedStatement select = connection.prepareStatement("SELECT count(*) FROM block_changes");
                 ResultSet rows = select.executeQuery()) {
                return rows.getInt(1);
            }
        });
    }

    private Void flush(Connection connection) throws SQLException {
        flushQueued.set(false);
        try (PreparedStatement upsert = connection.prepareStatement(UPSERT)) {
            BlockChange change;
            while ((change = unwritten.poll()) != null) {
                upsert.setString(1, change.world());
                upsert.setInt(2, change.pos().x());
                upsert.setInt(3, change.pos().y());
                upsert.setInt(4, change.pos().z());
                upsert.setString(5, change.player().toString());
                upsert.setString(6, change.kind().name());
                upsert.setString(7, change.material());
                upsert.setLong(8, change.changedAt());
                upsert.addBatch();
            }
            upsert.executeBatch();
        }
        return null;
    }

    private static boolean anyUnreported(Connection connection) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM block_changes WHERE changed_at > reported_at)");
             ResultSet rows = select.executeQuery()) {
            return rows.getBoolean(1);
        }
    }

    private static List<BlockChange> readAll(Connection connection) throws SQLException {
        List<BlockChange> changes = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT world, x, y, z, player, kind, material, changed_at, reported_at FROM block_changes");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                changes.add(new BlockChange(rows.getString(1), new BlockPos(rows.getInt(2), rows.getInt(3), rows.getInt(4)),
                        UUID.fromString(rows.getString(5)), ChangeKind.valueOf(rows.getString(6)), rows.getString(7),
                        rows.getLong(8), rows.getLong(9)));
            }
        }
        return changes;
    }

    private static void markReported(Connection connection, List<Cluster> clusters, long nowMillis) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(MARK_REPORTED)) {
            for (Cluster cluster : clusters) {
                for (BlockPos pos : cluster.positions()) {
                    update.setLong(1, nowMillis);
                    update.setString(2, cluster.world());
                    update.setInt(3, pos.x());
                    update.setInt(4, pos.y());
                    update.setInt(5, pos.z());
                    update.addBatch();
                }
            }
            update.executeBatch();
        }
    }
}
