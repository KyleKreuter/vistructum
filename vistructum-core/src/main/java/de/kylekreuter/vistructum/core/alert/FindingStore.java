package de.kylekreuter.vistructum.core.alert;

import de.kylekreuter.vistructum.api.BlockBox;
import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.Preview;
import de.kylekreuter.vistructum.api.Review;
import de.kylekreuter.vistructum.api.Source;
import de.kylekreuter.vistructum.api.Verdict;
import de.kylekreuter.vistructum.core.store.Binder;
import de.kylekreuter.vistructum.core.store.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class FindingStore {

    private static final String COLUMNS = "id, source, world, min_x, min_y, min_z, max_x, max_y, max_z, score, votes, "
            + "players, detail, model_version, created_at, verdict, reviewer, reviewed_at";
    private static final String OVERLAPPING = """
            SELECT EXISTS (SELECT 1 FROM findings WHERE world = ? AND created_at >= ?
                AND min_x <= ? AND max_x >= ? AND min_y <= ? AND max_y >= ? AND min_z <= ? AND max_z >= ?)
            """;
    private static final String INSERT = """
            INSERT INTO findings (source, world, min_x, min_y, min_z, max_x, max_y, max_z, score, votes, players, detail,
                model_version, preview_width, preview_height, preview, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final Database database;

    public FindingStore(Database database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    public CompletableFuture<Optional<Finding>> insertUnlessDuplicate(FindingDraft draft, Instant now, Duration dedupe) {
        return database.transaction(connection -> {
            if (overlapsRecent(connection, draft, now.minus(dedupe))) {
                return Optional.empty();
            }
            long id = insert(connection, draft, now);
            return select(connection, id);
        });
    }

    public CompletableFuture<Optional<Finding>> find(long id) {
        return database.transaction(connection -> select(connection, id));
    }

    public CompletableFuture<List<Finding>> open(int limit) {
        return database.transaction(connection -> list(connection,
                "SELECT " + COLUMNS + " FROM findings WHERE verdict IS NULL ORDER BY created_at DESC, id DESC LIMIT ?",
                statement -> statement.setInt(1, limit)));
    }

    public CompletableFuture<List<Finding>> since(Instant since, int limit) {
        return database.transaction(connection -> list(connection,
                "SELECT " + COLUMNS + " FROM findings WHERE created_at >= ? ORDER BY created_at DESC, id DESC LIMIT ?",
                statement -> {
                    statement.setLong(1, since.toEpochMilli());
                    statement.setInt(2, limit);
                }));
    }

    public CompletableFuture<Integer> countOpen() {
        return database.transaction(connection -> {
            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT count(*) FROM findings WHERE verdict IS NULL")) {
                return rows.getInt(1);
            }
        });
    }

    public CompletableFuture<Optional<Finding>> review(long id, Verdict verdict, String reviewer, Instant now) {
        return database.transaction(connection -> {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE findings SET verdict = ?, reviewer = ?, reviewed_at = ? WHERE id = ?")) {
                update.setString(1, verdict.name());
                update.setString(2, reviewer);
                update.setLong(3, now.toEpochMilli());
                update.setLong(4, id);
                update.executeUpdate();
            }
            return select(connection, id);
        });
    }

    public CompletableFuture<Optional<Preview>> preview(long id) {
        return database.transaction(connection -> {
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT preview_width, preview_height, preview FROM findings WHERE id = ?")) {
                select.setLong(1, id);
                try (ResultSet rows = select.executeQuery()) {
                    return rows.next()
                            ? Optional.of(new Preview(rows.getInt(1), rows.getInt(2), rows.getBytes(3)))
                            : Optional.empty();
                }
            }
        });
    }

    private static boolean overlapsRecent(Connection connection, FindingDraft draft, Instant cutoff) throws SQLException {
        BlockBox box = draft.box();
        try (PreparedStatement select = connection.prepareStatement(OVERLAPPING)) {
            select.setString(1, draft.world());
            select.setLong(2, cutoff.toEpochMilli());
            select.setInt(3, box.maxX());
            select.setInt(4, box.minX());
            select.setInt(5, box.maxY());
            select.setInt(6, box.minY());
            select.setInt(7, box.maxZ());
            select.setInt(8, box.minZ());
            try (ResultSet rows = select.executeQuery()) {
                return rows.getBoolean(1);
            }
        }
    }

    private static long insert(Connection connection, FindingDraft draft, Instant now) throws SQLException {
        BlockBox box = draft.box();
        try (PreparedStatement insert = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, draft.source().name());
            insert.setString(2, draft.world());
            insert.setInt(3, box.minX());
            insert.setInt(4, box.minY());
            insert.setInt(5, box.minZ());
            insert.setInt(6, box.maxX());
            insert.setInt(7, box.maxY());
            insert.setInt(8, box.maxZ());
            insert.setDouble(9, draft.score());
            insert.setInt(10, draft.votes());
            insert.setString(11, draft.players().stream().map(UUID::toString).sorted().collect(Collectors.joining(",")));
            insert.setString(12, draft.detail());
            insert.setString(13, draft.modelVersion());
            insert.setInt(14, draft.preview().width());
            insert.setInt(15, draft.preview().height());
            insert.setBytes(16, draft.preview().pixels());
            insert.setLong(17, now.toEpochMilli());
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static Optional<Finding> select(Connection connection, long id) throws SQLException {
        List<Finding> found = list(connection, "SELECT " + COLUMNS + " FROM findings WHERE id = ?",
                statement -> statement.setLong(1, id));
        return found.stream().findFirst();
    }

    private static List<Finding> list(Connection connection, String sql, Binder binder) throws SQLException {
        List<Finding> findings = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(sql)) {
            binder.bind(select);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    findings.add(read(rows));
                }
            }
        }
        return findings;
    }

    private static Finding read(ResultSet rows) throws SQLException {
        String verdict = rows.getString(16);
        Optional<Review> review = verdict == null ? Optional.empty() : Optional.of(new Review(Verdict.valueOf(verdict),
                rows.getString(17), Instant.ofEpochMilli(rows.getLong(18))));
        String players = rows.getString(12);
        Set<UUID> uuids = players.isEmpty() ? Set.of()
                : Arrays.stream(players.split(",")).map(UUID::fromString).collect(Collectors.toSet());
        return new Finding(rows.getLong(1), Source.valueOf(rows.getString(2)), rows.getString(3),
                new BlockBox(rows.getInt(4), rows.getInt(5), rows.getInt(6), rows.getInt(7), rows.getInt(8), rows.getInt(9)),
                rows.getDouble(10), rows.getInt(11), uuids, rows.getString(13), rows.getString(14),
                Instant.ofEpochMilli(rows.getLong(15)), review);
    }
}
