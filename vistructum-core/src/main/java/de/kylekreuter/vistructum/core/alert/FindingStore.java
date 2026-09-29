package de.kylekreuter.vistructum.core.alert;

import de.kylekreuter.vistructum.api.Activity;
import de.kylekreuter.vistructum.api.ActivityKind;
import de.kylekreuter.vistructum.api.BlockBox;
import de.kylekreuter.vistructum.api.DailyStats;
import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.FindingCandidate;
import de.kylekreuter.vistructum.api.FindingQuery;
import de.kylekreuter.vistructum.api.FindingStats;
import de.kylekreuter.vistructum.api.Heatmap;
import de.kylekreuter.vistructum.api.Preview;
import de.kylekreuter.vistructum.api.Review;
import de.kylekreuter.vistructum.api.ReviewerStats;
import de.kylekreuter.vistructum.api.SourcePrecision;
import de.kylekreuter.vistructum.api.Source;
import de.kylekreuter.vistructum.api.Thumbnail;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
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

    private static final String STORE_THUMBNAIL = """
            INSERT INTO finding_thumbnails (finding_id, width, height, pixels)
            SELECT id, ?, ?, ? FROM findings WHERE id = ?
            ON CONFLICT (finding_id) DO UPDATE SET width = excluded.width, height = excluded.height,
                pixels = excluded.pixels
            """;
    private static final String WITHOUT_THUMBNAIL = "SELECT " + COLUMNS + """
             FROM findings f
            WHERE id > ? AND NOT EXISTS (SELECT 1 FROM finding_thumbnails t WHERE t.finding_id = f.id)
            ORDER BY id LIMIT ?
            """;
    private static final int THUMBNAIL_PIXEL_BYTES = 3;
    private static final String STORED_SCENE = """
            SELECT f.source, s.kind, s.width, s.height, s.window_top, s.window_left, s.window_bottom, s.window_right,
                s.channels
            FROM findings f JOIN finding_scenes s ON s.finding_id = f.id
            WHERE f.id = ?
            """;
    private static final String STORE_HEATMAP = """
            INSERT INTO finding_heatmaps (finding_id, width, height, model_version, heat)
            SELECT id, ?, ?, ?, ? FROM findings WHERE id = ?
            ON CONFLICT (finding_id) DO UPDATE SET width = excluded.width, height = excluded.height,
                model_version = excluded.model_version, heat = excluded.heat
            """;
    private static final String REVIEWERS = """
            SELECT reviewer, sum(verdict = ?) AS confirmed, sum(verdict = ?) AS false_alarms
            FROM findings
            WHERE verdict IS NOT NULL AND reviewed_at >= ? AND reviewed_at < ?
            GROUP BY reviewer
            ORDER BY count(*) DESC, reviewer
            """;
    private static final String ACTIVITY = """
            SELECT at, actor, kind, finding_id FROM (
                SELECT reviewed_at AS at, reviewer AS actor, verdict AS kind, id AS finding_id, 0 AS rank
                FROM findings WHERE verdict IS NOT NULL AND reviewed_at < ?
                UNION ALL
                SELECT created_at, created_by, 'SHARED', finding_id, id * 2 FROM evidence_shares WHERE created_at < ?
                UNION ALL
                SELECT revoked_at, revoked_by, 'UNSHARED', finding_id, id * 2 + 1 FROM evidence_shares
                WHERE revoked_at IS NOT NULL AND revoked_at < ?)
            ORDER BY at DESC, rank DESC, finding_id DESC
            LIMIT ?
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
        query.player().ifPresent(player -> {
            sql.append(" AND instr(',' || players || ',', ?) > 0");
            arguments.add("," + player + ",");
        });
        switch (query.state()) {
            case OPEN -> sql.append(" AND verdict IS NULL");
            case REVIEWED -> sql.append(" AND verdict IS NOT NULL");
            case CONFIRMED -> {
                sql.append(" AND verdict = ?");
                arguments.add(Verdict.CONFIRMED.name());
            }
            case FALSE_ALARM -> {
                sql.append(" AND verdict = ?");
                arguments.add(Verdict.FALSE_ALARM.name());
            }
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

    public CompletableFuture<Optional<Thumbnail>> thumbnail(long id) {
        return database.transaction(connection -> {
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT width, height, pixels FROM finding_thumbnails WHERE finding_id = ?")) {
                select.setLong(1, id);
                try (ResultSet rows = select.executeQuery()) {
                    return rows.next()
                            ? Optional.of(decodeThumbnail(rows.getInt(1), rows.getInt(2), rows.getBytes(3)))
                            : Optional.empty();
                }
            }
        });
    }

    public CompletableFuture<Boolean> storeThumbnail(long id, Thumbnail thumbnail) {
        byte[] pixels = encodeThumbnail(thumbnail);
        return database.transaction(connection -> {
            try (PreparedStatement insert = connection.prepareStatement(STORE_THUMBNAIL)) {
                insert.setInt(1, thumbnail.width());
                insert.setInt(2, thumbnail.height());
                insert.setBytes(3, pixels);
                insert.setLong(4, id);
                return insert.executeUpdate() > 0;
            }
        });
    }

    public CompletableFuture<List<Finding>> withoutThumbnail(long afterId, int limit) {
        return database.transaction(connection -> List.copyOf(list(connection, WITHOUT_THUMBNAIL, statement -> {
            statement.setLong(1, afterId);
            statement.setInt(2, limit);
        })));
    }

    private static byte[] encodeThumbnail(Thumbnail thumbnail) {
        int[] pixels = thumbnail.pixels();
        byte[] bytes = new byte[pixels.length * THUMBNAIL_PIXEL_BYTES];
        for (int i = 0; i < pixels.length; i++) {
            bytes[i * THUMBNAIL_PIXEL_BYTES] = (byte) (pixels[i] >> 16);
            bytes[i * THUMBNAIL_PIXEL_BYTES + 1] = (byte) (pixels[i] >> 8);
            bytes[i * THUMBNAIL_PIXEL_BYTES + 2] = (byte) pixels[i];
        }
        return bytes;
    }

    private static Thumbnail decodeThumbnail(int width, int height, byte[] bytes) {
        int[] pixels = new int[bytes.length / THUMBNAIL_PIXEL_BYTES];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (bytes[i * THUMBNAIL_PIXEL_BYTES] & 0xFF) << 16
                    | (bytes[i * THUMBNAIL_PIXEL_BYTES + 1] & 0xFF) << 8
                    | bytes[i * THUMBNAIL_PIXEL_BYTES + 2] & 0xFF;
        }
        return new Thumbnail(width, height, pixels);
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

    public CompletableFuture<Integer> deleteReviewedBefore(Verdict verdict, Instant cutoff) {
        return database.transaction(connection -> {
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM findings WHERE verdict = ? AND reviewed_at < ?")) {
                delete.setString(1, verdict.name());
                delete.setLong(2, cutoff.toEpochMilli());
                return delete.executeUpdate();
            }
        });
    }

    public CompletableFuture<Optional<StoredScene>> scene(long id) {
        return database.transaction(connection -> {
            try (PreparedStatement select = connection.prepareStatement(STORED_SCENE)) {
                select.setLong(1, id);
                try (ResultSet rows = select.executeQuery()) {
                    return rows.next() ? Optional.of(new StoredScene(id, Source.valueOf(rows.getString(1)),
                            readInput(id, rows, 2))) : Optional.empty();
                }
            }
        });
    }

    public CompletableFuture<Optional<Heatmap>> heatmap(long id) {
        return database.transaction(connection -> {
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT width, height, heat FROM finding_heatmaps WHERE finding_id = ?")) {
                select.setLong(1, id);
                try (ResultSet rows = select.executeQuery()) {
                    if (!rows.next()) {
                        return Optional.empty();
                    }
                    byte[] heat = rows.getBytes(3);
                    int[] values = new int[heat.length];
                    for (int i = 0; i < heat.length; i++) {
                        values[i] = heat[i] & 0xFF;
                    }
                    return Optional.of(new Heatmap(rows.getInt(1), rows.getInt(2), values));
                }
            }
        });
    }

    public CompletableFuture<Boolean> storeHeatmap(long id, Heatmap heatmap, String modelVersion) {
        int[] values = heatmap.values();
        byte[] heat = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            heat[i] = (byte) values[i];
        }
        return database.transaction(connection -> {
            try (PreparedStatement insert = connection.prepareStatement(STORE_HEATMAP)) {
                insert.setInt(1, heatmap.width());
                insert.setInt(2, heatmap.height());
                insert.setString(3, modelVersion);
                insert.setBytes(4, heat);
                insert.setLong(5, id);
                return insert.executeUpdate() > 0;
            }
        });
    }

    public CompletableFuture<FindingStats> stats(Instant from, Instant to, ZoneId zone) {
        return database.transaction(connection -> new FindingStats(days(connection, from, to, zone),
                reviewers(connection, from, to), countOpen(connection), oldestOpen(connection)));
    }

    public CompletableFuture<List<Activity>> activity(Instant before, int limit) {
        return database.transaction(connection -> {
            List<Activity> entries = new ArrayList<>();
            try (PreparedStatement select = connection.prepareStatement(ACTIVITY)) {
                long cursor = before.toEpochMilli();
                select.setLong(1, cursor);
                select.setLong(2, cursor);
                select.setLong(3, cursor);
                select.setInt(4, limit);
                try (ResultSet rows = select.executeQuery()) {
                    while (rows.next()) {
                        entries.add(new Activity(Instant.ofEpochMilli(rows.getLong(1)), rows.getString(2),
                                ActivityKind.valueOf(rows.getString(3)), rows.getLong(4)));
                    }
                }
            }
            return List.copyOf(entries);
        });
    }

    private static List<DailyStats> days(Connection connection, Instant from, Instant to, ZoneId zone)
            throws SQLException {
        Map<LocalDate, Map<Source, int[]>> counts = new TreeMap<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT created_at, source, verdict FROM findings WHERE created_at >= ? AND created_at < ?")) {
            select.setLong(1, from.toEpochMilli());
            select.setLong(2, to.toEpochMilli());
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    LocalDate day = Instant.ofEpochMilli(rows.getLong(1)).atZone(zone).toLocalDate();
                    int[] count = counts.computeIfAbsent(day, d -> new EnumMap<>(Source.class))
                            .computeIfAbsent(Source.valueOf(rows.getString(2)), s -> new int[3]);
                    count[0]++;
                    String verdict = rows.getString(3);
                    if (verdict != null) {
                        count[Verdict.valueOf(verdict) == Verdict.CONFIRMED ? 1 : 2]++;
                    }
                }
            }
        }
        List<DailyStats> days = new ArrayList<>();
        counts.forEach((day, sources) -> sources.forEach((source, count) ->
                days.add(new DailyStats(day, source, count[0], count[1], count[2]))));
        return days;
    }

    private static List<ReviewerStats> reviewers(Connection connection, Instant from, Instant to) throws SQLException {
        List<ReviewerStats> reviewers = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(REVIEWERS)) {
            select.setString(1, Verdict.CONFIRMED.name());
            select.setString(2, Verdict.FALSE_ALARM.name());
            select.setLong(3, from.toEpochMilli());
            select.setLong(4, to.toEpochMilli());
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    reviewers.add(new ReviewerStats(rows.getString(1), rows.getInt(2), rows.getInt(3)));
                }
            }
        }
        return reviewers;
    }

    private static long countOpen(Connection connection) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT count(*) FROM findings WHERE verdict IS NULL");
             ResultSet rows = select.executeQuery()) {
            return rows.getLong(1);
        }
    }

    private static Optional<Instant> oldestOpen(Connection connection) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT min(created_at) FROM findings WHERE verdict IS NULL");
             ResultSet rows = select.executeQuery()) {
            long oldest = rows.getLong(1);
            return rows.wasNull() ? Optional.empty() : Optional.of(Instant.ofEpochMilli(oldest));
        }
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
        return new ReviewedScene(id, Source.valueOf(rows.getString(2)), Verdict.valueOf(rows.getString(3)),
                rows.getString(4), readInput(id, rows, 5));
    }

    private static ModelInput readInput(long id, ResultSet rows, int first) throws SQLException {
        String kind = rows.getString(first);
        ModelKind modelKind = ModelKind.byId(kind)
                .orElseThrow(() -> new SQLException("unknown model kind " + kind + " for finding " + id));
        return new ModelInput(modelKind, SceneBlob.decode(rows.getInt(first + 1), rows.getInt(first + 2),
                rows.getBytes(first + 7)), rows.getInt(first + 3), rows.getInt(first + 4), rows.getInt(first + 5),
                rows.getInt(first + 6));
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
