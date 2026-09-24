package de.kylekreuter.vistructum.core.scan;

import de.kylekreuter.vistructum.api.ScanCause;
import de.kylekreuter.vistructum.api.ScanJob;
import de.kylekreuter.vistructum.api.ScanStatus;
import de.kylekreuter.vistructum.core.store.Binder;
import de.kylekreuter.vistructum.core.store.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class ScanStore {

    private static final String COLUMNS =
            "id, world, cause, status, tiles_total, tiles_done, findings, failures, created_at";
    private static final String ACTIVE = "status IN ('QUEUED', 'RUNNING')";

    private final Database database;

    public ScanStore(Database database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    public CompletableFuture<Optional<ScanJob>> enqueue(String world, ScanCause cause, Instant now) {
        return database.transaction(connection -> enqueue(connection, world, cause, now));
    }

    public CompletableFuture<Optional<List<ScanJob>>> enqueueDaily(LocalDate day, Collection<String> worlds, Instant now) {
        return database.transaction(connection -> {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT OR IGNORE INTO daily_scans (day) VALUES (?)")) {
                insert.setString(1, day.toString());
                if (insert.executeUpdate() == 0) {
                    return Optional.empty();
                }
            }
            List<ScanJob> jobs = new ArrayList<>();
            for (String world : worlds) {
                enqueue(connection, world, ScanCause.DAILY, now).ifPresent(jobs::add);
            }
            return Optional.of(jobs);
        });
    }

    public CompletableFuture<Optional<ScanJob>> next() {
        return database.transaction(connection -> first(connection,
                "SELECT " + COLUMNS + " FROM scan_jobs WHERE " + ACTIVE + " ORDER BY id LIMIT 1", Binder.NONE));
    }

    public CompletableFuture<List<ScanJob>> active() {
        return database.transaction(connection -> list(connection,
                "SELECT " + COLUMNS + " FROM scan_jobs WHERE " + ACTIVE + " ORDER BY id", Binder.NONE));
    }

    public CompletableFuture<ScanJob> plan(long jobId, Collection<ScanPlan.Tile> tiles) {
        return database.transaction(connection -> {
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM scan_tiles WHERE job_id = ?")) {
                delete.setLong(1, jobId);
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO scan_tiles (job_id, origin_x, origin_z) VALUES (?, ?, ?)")) {
                for (ScanPlan.Tile tile : tiles) {
                    insert.setLong(1, jobId);
                    insert.setInt(2, tile.originX());
                    insert.setInt(3, tile.originZ());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE scan_jobs SET status = 'RUNNING', tiles_total = ?, tiles_done = 0 WHERE id = ?")) {
                update.setInt(1, tiles.size());
                update.setLong(2, jobId);
                update.executeUpdate();
            }
            return byId(connection, jobId);
        });
    }

    public CompletableFuture<Optional<ScanPlan.Tile>> nextTile(long jobId) {
        return database.transaction(connection -> {
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT origin_x, origin_z FROM scan_tiles WHERE job_id = ? AND done = 0 "
                            + "ORDER BY origin_x, origin_z LIMIT 1")) {
                select.setLong(1, jobId);
                try (ResultSet rows = select.executeQuery()) {
                    return rows.next() ? Optional.of(new ScanPlan.Tile(rows.getInt(1), rows.getInt(2))) : Optional.empty();
                }
            }
        });
    }

    public CompletableFuture<Void> completeTile(long jobId, ScanPlan.Tile tile, int findings, boolean failed) {
        return database.transaction(connection -> {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE scan_tiles SET done = 1 WHERE job_id = ? AND origin_x = ? AND origin_z = ? AND done = 0")) {
                update.setLong(1, jobId);
                update.setInt(2, tile.originX());
                update.setInt(3, tile.originZ());
                if (update.executeUpdate() == 0) {
                    return null;
                }
            }
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE scan_jobs SET tiles_done = tiles_done + 1, findings = findings + ?, failures = failures + ? "
                            + "WHERE id = ?")) {
                update.setInt(1, findings);
                update.setInt(2, failed ? 1 : 0);
                update.setLong(3, jobId);
                update.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<ScanJob> finish(long jobId, ScanStatus status, Instant now) {
        return database.transaction(connection -> {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE scan_jobs SET status = ?, finished_at = ? WHERE id = ?")) {
                update.setString(1, status.name());
                update.setLong(2, now.toEpochMilli());
                update.setLong(3, jobId);
                update.executeUpdate();
            }
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM scan_tiles WHERE job_id = ?")) {
                delete.setLong(1, jobId);
                delete.executeUpdate();
            }
            return byId(connection, jobId);
        });
    }

    public CompletableFuture<Integer> cancelAll(Instant now) {
        return database.transaction(connection -> {
            int cancelled;
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE scan_jobs SET status = 'CANCELLED', finished_at = ? WHERE " + ACTIVE)) {
                update.setLong(1, now.toEpochMilli());
                cancelled = update.executeUpdate();
            }
            try (Statement delete = connection.createStatement()) {
                delete.executeUpdate("DELETE FROM scan_tiles WHERE job_id NOT IN (SELECT id FROM scan_jobs WHERE "
                        + ACTIVE + ")");
            }
            return cancelled;
        });
    }

    private static Optional<ScanJob> enqueue(Connection connection, String world, ScanCause cause, Instant now)
            throws SQLException {
        Optional<ScanJob> active = first(connection,
                "SELECT " + COLUMNS + " FROM scan_jobs WHERE world = ? AND " + ACTIVE + " LIMIT 1",
                statement -> statement.setString(1, world));
        if (active.isPresent()) {
            return Optional.empty();
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO scan_jobs (world, cause, status, created_at) VALUES (?, ?, 'QUEUED', ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, world);
            insert.setString(2, cause.name());
            insert.setLong(3, now.toEpochMilli());
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                keys.next();
                return Optional.of(byId(connection, keys.getLong(1)));
            }
        }
    }

    private static ScanJob byId(Connection connection, long id) throws SQLException {
        return first(connection, "SELECT " + COLUMNS + " FROM scan_jobs WHERE id = ?", statement -> statement.setLong(1, id))
                .orElseThrow(() -> new SQLException("scan job " + id + " does not exist"));
    }

    private static Optional<ScanJob> first(Connection connection, String sql, Binder binder) throws SQLException {
        return list(connection, sql, binder).stream().findFirst();
    }

    private static List<ScanJob> list(Connection connection, String sql, Binder binder) throws SQLException {
        List<ScanJob> jobs = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(sql)) {
            binder.bind(select);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    jobs.add(new ScanJob(rows.getLong(1), rows.getString(2), ScanCause.valueOf(rows.getString(3)),
                            ScanStatus.valueOf(rows.getString(4)), rows.getInt(5), rows.getInt(6), rows.getInt(7),
                            rows.getInt(8), Instant.ofEpochMilli(rows.getLong(9))));
                }
            }
        }
        return jobs;
    }
}
