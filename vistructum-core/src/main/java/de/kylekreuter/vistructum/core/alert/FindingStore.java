package de.kylekreuter.vistructum.core.alert;

import de.kylekreuter.vistructum.api.BlockBox;
import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.FindingCandidate;
import de.kylekreuter.vistructum.api.FindingQuery;
import de.kylekreuter.vistructum.api.Preview;
import de.kylekreuter.vistructum.api.Review;
import de.kylekreuter.vistructum.api.SourcePrecision;
import de.kylekreuter.vistructum.api.Source;
import de.kylekreuter.vistructum.api.Verdict;
import de.kylekreuter.vistructum.core.store.Binder;
import de.kylekreuter.vistructum.core.store.Database;
import de.kylekreuter.vistructum.inference.ModelKind;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
    private static final String INSERT_SCENE = """
            INSERT INTO finding_scenes (finding_id, kind, width, height, window_top, window_left, window_bottom,
                window_right, channels)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String REVIEWED_SCENES = """
            SELECT f.id, f.source, f.verdict, f.model_version, s.kind, s.width, s.height, s.window_top, s.window_left,
                s.window_bottom, s.window_right, s.channels
            FROM findings f JOIN finding_scenes s ON s.finding_id = f.id
            WHERE f.verdict IS NOT NULL AND f.id > ?
            ORDER BY f.id LIMIT ?
            """;
    private static final String REVIEWED_WITHOUT_SCENE = """
            SELECT count(*) FROM findings f
            WHERE f.verdict IS NOT NULL AND NOT EXISTS (SELECT 1 FROM finding_scenes s WHERE s.finding_id = f.id)
            """;

    private final Database database;

    public FindingStore(Database database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    public CompletableFuture<Optional<Finding>> insertUnlessDuplicate(DetectedCandidate detected, Instant now,
                                                                     Duration dedupe) {
        FindingCandidate candidate = detected.candidate();
        byte[] channels = SceneBlob.encode(detected.input().scene());
        return database.transaction(connection -> {
            if (overlapsRecent(connection, candidate, now.minus(dedupe))) {
                return Optional.empty();
            }
            long id = insert(connection, candidate, now);
            insertScene(connection, id, detected.input(), channels);
            return select(connection, id);
        });
    }

    public CompletableFuture<Optional<Finding>> find(long id) {
        return database.transaction(connection -> select(connection, id));
    }

    public CompletableFuture<Boolean> isDuplicate(FindingCandidate candidate, Instant now, Duration dedupe) {
        return database.transaction(connection -> overlapsRecent(connection, candidate, now.minus(dedupe)));
    }

    public CompletableFuture<FindingSlice> query(FindingQuery query) {
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM findings WHERE 1 = 1");
        List<Object> arguments = new ArrayList<>();
        appendCriteria(query, sql, arguments);
        query.beforeId().ifPresent(id -> {
            sql.append(" AND id < ?");
            arguments.add(id);
        });
        sql.append(" ORDER BY id DESC LIMIT ?");
        arguments.add(query.limit() + 1);
        return database.transaction(connection -> {
            List<Finding> rows = list(connection, sql.toString(), bindAll(arguments));
            boolean more = rows.size() > query.limit();
            return new FindingSlice(more ? rows.subList(0, query.limit()) : rows, more);
        });
    }

    public CompletableFuture<Long> count(FindingQuery query) {
        StringBuilder sql = new StringBuilder("SELECT count(*) FROM findings WHERE 1 = 1");
        List<Object> arguments = new ArrayList<>();
        appendCriteria(query, sql, arguments);
        return database.transaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                bindAll(arguments).bind(statement);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.getLong(1);
                }
            }
        });
    }

    private static void appendCriteria(FindingQuery query, StringBuilder sql, List<Object> arguments) {
        query.world().ifPresent(world -> {
            sql.append(" AND world = ?");
            arguments.add(world);
        });
        query.source().ifPresent(source -> {
            sql.append(" AND source = ?");
            arguments.add(source.name());
        });
        switch (query.state()) {
            case OPEN -> sql.append(" AND verdict IS NULL");
            case REVIEWED -> sql.append(" AND verdict IS NOT NULL");
            case ANY -> {
            }
        }
        query.since().ifPresent(since -> {
            sql.append(" AND created_at >= ?");
            arguments.add(since.toEpochMilli());
        });
    }

    private static Binder bindAll(List<Object> arguments) {
        return statement -> {
            for (int i = 0; i < arguments.size(); i++) {
                statement.setObject(i + 1, arguments.get(i));
            }
        };
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

    public CompletableFuture<List<SourcePrecision>> precision() {
        return database.transaction(connection -> {
            Map<Source, long[]> counts = new EnumMap<>(Source.class);
            for (Source source : Source.values()) {
                counts.put(source, new long[Verdict.values().length]);
            }
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT source, verdict, count(*) FROM findings WHERE verdict IS NOT NULL GROUP BY source, verdict");
                 ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    counts.get(Source.valueOf(rows.getString(1)))[Verdict.valueOf(rows.getString(2)).ordinal()]
                            = rows.getLong(3);
                }
            }
            return counts.entrySet().stream().map(entry -> new SourcePrecision(entry.getKey(),
                    entry.getValue()[Verdict.CONFIRMED.ordinal()], entry.getValue()[Verdict.FALSE_ALARM.ordinal()]))
                    .toList();
        });
    }

    public CompletableFuture<List<ReviewedScene>> reviewedScenes(long afterId, int limit) {
        return database.transaction(connection -> {
            List<ReviewedScene> scenes = new ArrayList<>();
            try (PreparedStatement select = connection.prepareStatement(REVIEWED_SCENES)) {
                select.setLong(1, afterId);
                select.setInt(2, limit);
                try (ResultSet rows = select.executeQuery()) {
                    while (rows.next()) {
                        scenes.add(readScene(rows));
                    }
                }
            }
            return scenes;
        });
    }

    public CompletableFuture<Long> countReviewedWithoutScene() {
        return database.transaction(connection -> {
            try (PreparedStatement select = connection.prepareStatement(REVIEWED_WITHOUT_SCENE);
                 ResultSet rows = select.executeQuery()) {
                return rows.getLong(1);
            }
        });
    }

    public CompletableFuture<Integer> deleteReviewedBefore(Instant cutoff) {
        return database.transaction(connection -> {
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM findings WHERE verdict IS NOT NULL AND reviewed_at < ?")) {
                delete.setLong(1, cutoff.toEpochMilli());
                return delete.executeUpdate();
            }
        });
    }

    private static boolean overlapsRecent(Connection connection, FindingCandidate candidate, Instant cutoff) throws SQLException {
        BlockBox box = candidate.box();
        try (PreparedStatement select = connection.prepareStatement(OVERLAPPING)) {
            select.setString(1, candidate.world());
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

    private static long insert(Connection connection, FindingCandidate candidate, Instant now) throws SQLException {
        BlockBox box = candidate.box();
        try (PreparedStatement insert = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, candidate.source().name());
            insert.setString(2, candidate.world());
            insert.setInt(3, box.minX());
            insert.setInt(4, box.minY());
            insert.setInt(5, box.minZ());
            insert.setInt(6, box.maxX());
            insert.setInt(7, box.maxY());
            insert.setInt(8, box.maxZ());
            insert.setDouble(9, candidate.score());
            insert.setInt(10, candidate.votes());
            insert.setString(11, candidate.players().stream().map(UUID::toString).sorted().collect(Collectors.joining(",")));
            insert.setString(12, candidate.detail());
            insert.setString(13, candidate.modelVersion());
            insert.setInt(14, candidate.preview().width());
            insert.setInt(15, candidate.preview().height());
            insert.setBytes(16, candidate.preview().pixels());
            insert.setLong(17, now.toEpochMilli());
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static void insertScene(Connection connection, long id, ModelInput input, byte[] channels)
            throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(INSERT_SCENE)) {
            insert.setLong(1, id);
            insert.setString(2, input.kind().id());
            insert.setInt(3, input.scene().width());
            insert.setInt(4, input.scene().height());
            insert.setInt(5, input.top());
            insert.setInt(6, input.left());
            insert.setInt(7, input.bottom());
            insert.setInt(8, input.right());
            insert.setBytes(9, channels);
            insert.executeUpdate();
        }
    }

    private static ReviewedScene readScene(ResultSet rows) throws SQLException {
        long id = rows.getLong(1);
        String kind = rows.getString(5);
        ModelKind modelKind = ModelKind.byId(kind)
                .orElseThrow(() -> new SQLException("unknown model kind " + kind + " for finding " + id));
        ModelInput input = new ModelInput(modelKind, SceneBlob.decode(rows.getInt(6), rows.getInt(7), rows.getBytes(12)),
                rows.getInt(8), rows.getInt(9), rows.getInt(10), rows.getInt(11));
        return new ReviewedScene(id, Source.valueOf(rows.getString(2)), Verdict.valueOf(rows.getString(3)),
                rows.getString(4), input);
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
