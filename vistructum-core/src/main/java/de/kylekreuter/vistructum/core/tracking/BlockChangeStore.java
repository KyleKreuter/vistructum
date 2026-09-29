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

    private static final String INSERT = """
            INSERT INTO block_events (world, x, y, z, player, player_name, kind, block_data, previous_data, changed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String LATEST_PER_POSITION_AND_PLAYER = """
            SELECT world, x, y, z, player, kind, block_data, changed_at, reported_at FROM (
                SELECT world, x, y, z, player, kind, block_data, changed_at,
                    max(reported_at) OVER (PARTITION BY world, x, y, z, player) AS reported_at,
                    row_number() OVER (PARTITION BY world, x, y, z, player ORDER BY changed_at DESC, id DESC) AS newest
                FROM block_events)
            WHERE newest = 1
            """;
    private static final String MARK_REPORTED =
            "UPDATE block_events SET reported_at = ? WHERE world = ? AND x = ? AND z = ? AND y = ?";

    private final Database database;
    private final Queue<TrackedChange> unwritten = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean flushQueued = new AtomicBoolean();

    public BlockChangeStore(Database database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    public void record(TrackedChange change) {
        unwritten.add(Objects.requireNonNull(change, "change"));
        if (flushQueued.compareAndSet(false, true)) {
            database.transaction(this::flush);
        }
    }

    public CompletableFuture<List<Cluster>> takeReady(ClusterSettings settings, long nowMillis) {
        return database.transaction(connection -> {
            flush(connection);
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM block_events WHERE changed_at < ?")) {
                delete.setLong(1, nowMillis - settings.ttl().toMillis());
                delete.executeUpdate();
            }
            if (!anyUnreported(connection)) {
                return List.of();
            }
            List<Cluster> ready = Clustering.ready(latest(connection), settings, nowMillis);
            markReported(connection, ready, nowMillis);
            return ready;
        });
    }

    public CompletableFuture<Integer> count() {
        return database.transaction(connection -> {
            try (PreparedStatement select = connection.prepareStatement("SELECT count(*) FROM (SELECT DISTINCT world, x, y, z, player FROM block_events)");
                 ResultSet rows = select.executeQuery()) {
                return rows.getInt(1);
            }
        });
    }

    private Void flush(Connection connection) throws SQLException {
        flushQueued.set(false);
        try (PreparedStatement insert = connection.prepareStatement(INSERT)) {
            TrackedChange change;
            while ((change = unwritten.poll()) != null) {
                insert.setString(1, change.world());
                insert.setInt(2, change.pos().x());
                insert.setInt(3, change.pos().y());
                insert.setInt(4, change.pos().z());
                insert.setString(5, change.player().toString());
                insert.setString(6, change.playerName());
                insert.setString(7, change.kind().name());
                insert.setString(8, change.blockData());
                insert.setString(9, change.previousData());
                insert.setLong(10, change.changedAt());
                insert.addBatch();
            }
            insert.executeBatch();
        }
        return null;
    }

    private static boolean anyUnreported(Connection connection) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM block_events WHERE changed_at > reported_at)");
             ResultSet rows = select.executeQuery()) {
            return rows.getBoolean(1);
        }
    }

    private static List<BlockChange> latest(Connection connection) throws SQLException {
        List<BlockChange> changes = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(LATEST_PER_POSITION_AND_PLAYER);
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                changes.add(new BlockChange(rows.getString(1), new BlockPos(rows.getInt(2), rows.getInt(3), rows.getInt(4)),
                        UUID.fromString(rows.getString(5)), ChangeKind.valueOf(rows.getString(6)),
                        TrackedChange.material(rows.getString(7)), rows.getLong(8), rows.getLong(9)));
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
                    update.setInt(4, pos.z());
                    update.setInt(5, pos.y());
                    update.addBatch();
                }
            }
            update.executeBatch();
        }
    }
}
