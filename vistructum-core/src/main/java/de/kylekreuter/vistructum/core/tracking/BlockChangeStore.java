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
    private static final String CELL_CHANGES = """
            SELECT x, y, z, player, kind, block_data, changed_at, reported_at FROM (
                SELECT id, x, y, z, player, kind, block_data, changed_at,
                    max(reported_at) OVER (PARTITION BY x, y, z, player) AS reported_at,
                    row_number() OVER (PARTITION BY x, y, z, player ORDER BY changed_at DESC, id DESC) AS newest
                FROM block_events WHERE world = ? AND x >> 4 = ? AND z >> 4 = ?)
            WHERE newest = 1
            ORDER BY changed_at, id
            """;
    private static final String CELL_SUMMARIES = """
            SELECT x, y, z, max(changed_at), max(changed_at) > max(reported_at) FROM block_events
            WHERE world = ? AND x >> 4 = ? AND z >> 4 = ?
            GROUP BY x, y, z
            """;
    private static final String SETTLED = """
            SELECT DISTINCT world, x, y, z FROM block_events
            WHERE changed_at > ? AND changed_at <= ? AND changed_at > reported_at
            """;
    private static final String VANISHING = """
            SELECT DISTINCT world, x, y, z FROM block_events e INDEXED BY block_events_by_time
            WHERE changed_at < ? AND NOT EXISTS (
                SELECT 1 FROM block_events n INDEXED BY block_events_by_position_time
                WHERE n.world = e.world AND n.x = e.x AND n.z = e.z AND n.y = e.y AND n.changed_at >= ?)
            """;
    private static final String CHECKED_UNTIL = "SELECT checked_until FROM tracking_state WHERE id = 1";
    private static final String STORE_CHECKED_UNTIL = """
            INSERT INTO tracking_state (id, checked_until) VALUES (1, ?)
            ON CONFLICT (id) DO UPDATE SET checked_until = excluded.checked_until
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
            long expiredBefore = nowMillis - settings.ttl().toMillis();
            List<WorldPosition> vanished = vanishing(connection, expiredBefore);
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM block_events WHERE changed_at < ?")) {
                delete.setLong(1, expiredBefore);
                delete.executeUpdate();
            }
            long checkedUntil = checkedUntil(connection);
            long settledUntil = nowMillis - settings.quietPeriod().toMillis();
            List<WorldPosition> settled = settled(connection, checkedUntil, settledUntil);
            if (settledUntil > checkedUntil) {
                storeCheckedUntil(connection, settledUntil);
            }
            if (settled.isEmpty() && vanished.isEmpty()) {
                return List.of();
            }
            List<Cluster> ready;
            try (PreparedStatement summaries = connection.prepareStatement(CELL_SUMMARIES);
                 PreparedStatement changes = connection.prepareStatement(CELL_CHANGES)) {
                ready = Clustering.ready(settled, vanished, new StoredCells(summaries, changes), settings, nowMillis);
            }
            markReported(connection, ready, nowMillis);
            return ready.stream().filter(cluster -> !cluster.oversized()).toList();
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

    private static List<WorldPosition> vanishing(Connection connection, long expiredBefore) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(VANISHING)) {
            select.setLong(1, expiredBefore);
            select.setLong(2, expiredBefore);
            return positions(select);
        }
    }

    private static List<WorldPosition> settled(Connection connection, long after, long until) throws SQLException {
        if (until <= after) {
            return List.of();
        }
        try (PreparedStatement select = connection.prepareStatement(SETTLED)) {
            select.setLong(1, after);
            select.setLong(2, until);
            return positions(select);
        }
    }

    private static List<WorldPosition> positions(PreparedStatement select) throws SQLException {
        List<WorldPosition> positions = new ArrayList<>();
        try (ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                positions.add(new WorldPosition(rows.getString(1), new BlockPos(rows.getInt(2), rows.getInt(3),
                        rows.getInt(4))));
            }
        }
        return positions;
    }

    private static long checkedUntil(Connection connection) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(CHECKED_UNTIL);
             ResultSet rows = select.executeQuery()) {
            return rows.next() ? rows.getLong(1) : Long.MIN_VALUE;
        }
    }

    private static void storeCheckedUntil(Connection connection, long checkedUntil) throws SQLException {
        try (PreparedStatement upsert = connection.prepareStatement(STORE_CHECKED_UNTIL)) {
            upsert.setLong(1, checkedUntil);
            upsert.executeUpdate();
        }
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

    private record StoredCells(PreparedStatement summaries, PreparedStatement changes) implements CellLoader {

        @Override
        public List<PositionSummary> summaries(String world, int cellX, int cellZ) throws SQLException {
            select(summaries, world, cellX, cellZ);
            List<PositionSummary> positions = new ArrayList<>();
            try (ResultSet rows = summaries.executeQuery()) {
                while (rows.next()) {
                    positions.add(new PositionSummary(new BlockPos(rows.getInt(1), rows.getInt(2), rows.getInt(3)),
                            rows.getLong(4), rows.getBoolean(5)));
                }
            }
            return positions;
        }

        @Override
        public List<BlockChange> changes(String world, int cellX, int cellZ) throws SQLException {
            select(changes, world, cellX, cellZ);
            List<BlockChange> cellChanges = new ArrayList<>();
            try (ResultSet rows = changes.executeQuery()) {
                while (rows.next()) {
                    cellChanges.add(new BlockChange(world, new BlockPos(rows.getInt(1), rows.getInt(2), rows.getInt(3)),
                            UUID.fromString(rows.getString(4)), ChangeKind.valueOf(rows.getString(5)),
                            TrackedChange.material(rows.getString(6)), rows.getLong(7), rows.getLong(8)));
                }
            }
            return cellChanges;
        }

        private static void select(PreparedStatement select, String world, int cellX, int cellZ) throws SQLException {
            select.setString(1, world);
            select.setInt(2, cellX);
            select.setInt(3, cellZ);
        }
    }
}
