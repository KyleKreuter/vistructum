package de.kylekreuter.vistructum.core.evidence;

import de.kylekreuter.vistructum.api.BlockAction;
import de.kylekreuter.vistructum.api.BlockBox;
import de.kylekreuter.vistructum.api.BlockEvent;
import de.kylekreuter.vistructum.api.BlockVolume;
import de.kylekreuter.vistructum.api.Evidence;
import de.kylekreuter.vistructum.api.MotionFrame;
import de.kylekreuter.vistructum.api.Recording;
import de.kylekreuter.vistructum.core.recording.MotionCodec;
import de.kylekreuter.vistructum.core.store.Database;
import de.kylekreuter.vistructum.core.store.Packed;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class EvidenceStore {

    private static final String LOGGED_CHANGES = """
            SELECT x, y, z, player, player_name, kind, block_data, previous_data, changed_at FROM block_events
            WHERE world = ? AND x BETWEEN ? AND ? AND z BETWEEN ? AND ? AND y BETWEEN ? AND ? AND changed_at <= ?
            ORDER BY changed_at, id
            """;
    private static final String INSERT_CHANGE = """
            INSERT INTO finding_changes (finding_id, seq, x, y, z, player, player_name, kind, block_data, changed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String INSERT_EVIDENCE = """
            INSERT INTO finding_evidence (finding_id, min_x, min_y, min_z, size_x, size_y, size_z, palette, cells, from_ms,
                to_ms)
            SELECT id, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? FROM findings WHERE id = ?
            """;
    private static final String NEARBY_CHUNKS = """
            SELECT player, frames FROM motion_chunks
            WHERE world = ? AND end_ms >= ? AND start_ms <= ?
                AND max_x >= ? AND min_x <= ? AND max_y >= ? AND min_y <= ? AND max_z >= ? AND min_z <= ?
            """;
    private static final String COPY_CHUNKS = """
            INSERT OR IGNORE INTO finding_recordings (finding_id, chunk_id, player, player_name, start_ms, end_ms, frames)
            SELECT ?, id, player, player_name, start_ms, end_ms, frames FROM motion_chunks
            WHERE world = ? AND player = ? AND end_ms >= ? AND start_ms <= ?
            """;
    private static final String PALETTE_SEPARATOR = "\n";

    private final Database database;

    public EvidenceStore(Database database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    public CompletableFuture<Boolean> secure(long findingId, String world, BlockBox box, VolumeSnapshot current,
                                             EvidenceSettings settings, Instant createdAt) {
        return database.transaction(connection -> {
            long to = createdAt.toEpochMilli();
            List<LoggedChange> changes = loggedChanges(connection, world, current.region(), to);
            long firstChange = changes.isEmpty() ? to : changes.getFirst().changedAt();
            long from = firstChange - settings.lead().toMillis();
            VolumeSnapshot before = before(current, changes);
            if (!insertEvidence(connection, findingId, before, from, to)) {
                return false;
            }
            insertChanges(connection, findingId, changes);
            for (UUID player : nearbyPlayers(connection, world, box, settings.radius(), from, to)) {
                try (PreparedStatement copy = connection.prepareStatement(COPY_CHUNKS)) {
                    copy.setLong(1, findingId);
                    copy.setString(2, world);
                    copy.setString(3, player.toString());
                    copy.setLong(4, from);
                    copy.setLong(5, to);
                    copy.executeUpdate();
                }
            }
            return true;
        });
    }

    public CompletableFuture<Boolean> hasEvidence(long findingId) {
        return database.transaction(connection -> {
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT EXISTS (SELECT 1 FROM finding_evidence WHERE finding_id = ?)")) {
                select.setLong(1, findingId);
                try (ResultSet rows = select.executeQuery()) {
                    return rows.getBoolean(1);
                }
            }
        });
    }

    public CompletableFuture<Optional<Evidence>> evidence(long findingId) {
        return database.transaction(connection -> {
            try (PreparedStatement select = connection.prepareStatement("""
                    SELECT min_x, min_y, min_z, size_x, size_y, size_z, palette, cells, from_ms, to_ms
                    FROM finding_evidence WHERE finding_id = ?
                    """)) {
                select.setLong(1, findingId);
                try (ResultSet rows = select.executeQuery()) {
                    if (!rows.next()) {
                        return Optional.empty();
                    }
                    int sizeX = rows.getInt(4);
                    int sizeY = rows.getInt(5);
                    int sizeZ = rows.getInt(6);
                    BlockVolume before = new BlockVolume(rows.getInt(1), rows.getInt(2), rows.getInt(3), sizeX, sizeY,
                            sizeZ, Arrays.asList(rows.getString(7).split(PALETTE_SEPARATOR, -1)),
                            decodeCells(rows.getBytes(8), sizeX * sizeY * sizeZ));
                    long from = rows.getLong(9);
                    long to = rows.getLong(10);
                    return Optional.of(new Evidence(findingId, before, storedChanges(connection, findingId),
                            recordings(connection, findingId, from, to)));
                }
            }
        });
    }

    static VolumeSnapshot before(VolumeSnapshot current, List<LoggedChange> changes) {
        Map<Integer, String> firstPrevious = new HashMap<>();
        for (LoggedChange change : changes) {
            firstPrevious.putIfAbsent(current.index(change.x(), change.y(), change.z()), change.previousData());
        }
        VolumeSnapshot.Builder before = new VolumeSnapshot.Builder(current.region());
        int[] cells = current.cells();
        for (int i = 0; i < cells.length; i++) {
            before.set(i, firstPrevious.getOrDefault(i, current.palette().get(cells[i])));
        }
        return before.build();
    }

    private static List<LoggedChange> loggedChanges(Connection connection, String world, BlockBox region, long to)
            throws SQLException {
        List<LoggedChange> changes = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(LOGGED_CHANGES)) {
            select.setString(1, world);
            select.setInt(2, region.minX());
            select.setInt(3, region.maxX());
            select.setInt(4, region.minZ());
            select.setInt(5, region.maxZ());
            select.setInt(6, region.minY());
            select.setInt(7, region.maxY());
            select.setLong(8, to);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    changes.add(new LoggedChange(rows.getInt(1), rows.getInt(2), rows.getInt(3),
                            UUID.fromString(rows.getString(4)), rows.getString(5), BlockAction.valueOf(rows.getString(6)),
                            rows.getString(7), rows.getString(8), rows.getLong(9)));
                }
            }
        }
        return changes;
    }

    private static boolean insertEvidence(Connection connection, long findingId, VolumeSnapshot before, long from,
                                          long to) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(INSERT_EVIDENCE)) {
            insert.setInt(1, before.region().minX());
            insert.setInt(2, before.region().minY());
            insert.setInt(3, before.region().minZ());
            insert.setInt(4, before.sizeX());
            insert.setInt(5, before.sizeY());
            insert.setInt(6, before.sizeZ());
            insert.setString(7, String.join(PALETTE_SEPARATOR, before.palette()));
            insert.setBytes(8, encodeCells(before.cells()));
            insert.setLong(9, from);
            insert.setLong(10, to);
            insert.setLong(11, findingId);
            return insert.executeUpdate() > 0;
        }
    }

    private static void insertChanges(Connection connection, long findingId, List<LoggedChange> changes)
            throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(INSERT_CHANGE)) {
            for (int i = 0; i < changes.size(); i++) {
                LoggedChange change = changes.get(i);
                insert.setLong(1, findingId);
                insert.setInt(2, i);
                insert.setInt(3, change.x());
                insert.setInt(4, change.y());
                insert.setInt(5, change.z());
                insert.setString(6, change.player().toString());
                insert.setString(7, change.playerName());
                insert.setString(8, change.action().name());
                insert.setString(9, change.blockData());
                insert.setLong(10, change.changedAt());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static Set<UUID> nearbyPlayers(Connection connection, String world, BlockBox box, int radius, long from,
                                           long to) throws SQLException {
        Set<UUID> players = new LinkedHashSet<>();
        try (PreparedStatement select = connection.prepareStatement(NEARBY_CHUNKS)) {
            select.setString(1, world);
            select.setLong(2, from);
            select.setLong(3, to);
            select.setDouble(4, box.minX() - radius);
            select.setDouble(5, box.maxX() + 1 + radius);
            select.setDouble(6, box.minY() - radius);
            select.setDouble(7, box.maxY() + 1 + radius);
            select.setDouble(8, box.minZ() - radius);
            select.setDouble(9, box.maxZ() + 1 + radius);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    UUID player = UUID.fromString(rows.getString(1));
                    if (!players.contains(player) && MotionCodec.decode(rows.getBytes(2)).stream()
                            .anyMatch(frame -> frame.atMillis() >= from && frame.atMillis() <= to
                                    && near(frame, box, radius))) {
                        players.add(player);
                    }
                }
            }
        }
        return players;
    }

    static boolean near(MotionFrame frame, BlockBox box, int radius) {
        double dx = distance(frame.x(), box.minX(), box.maxX() + 1);
        double dy = distance(frame.y(), box.minY(), box.maxY() + 1);
        double dz = distance(frame.z(), box.minZ(), box.maxZ() + 1);
        return dx * dx + dy * dy + dz * dz <= (double) radius * radius;
    }

    private static double distance(double value, double min, double max) {
        return value < min ? min - value : value > max ? value - max : 0.0;
    }

    private static List<BlockEvent> storedChanges(Connection connection, long findingId) throws SQLException {
        List<BlockEvent> changes = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT changed_at, player, player_name, kind, x, y, z, block_data FROM finding_changes
                WHERE finding_id = ? ORDER BY seq
                """)) {
            select.setLong(1, findingId);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    changes.add(new BlockEvent(Instant.ofEpochMilli(rows.getLong(1)), UUID.fromString(rows.getString(2)),
                            rows.getString(3), BlockAction.valueOf(rows.getString(4)), rows.getInt(5), rows.getInt(6),
                            rows.getInt(7), rows.getString(8)));
                }
            }
        }
        return changes;
    }

    private static List<Recording> recordings(Connection connection, long findingId, long from, long to)
            throws SQLException {
        Map<UUID, String> names = new LinkedHashMap<>();
        Map<UUID, List<MotionFrame>> frames = new LinkedHashMap<>();
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT player, player_name, frames FROM finding_recordings WHERE finding_id = ? ORDER BY start_ms, chunk_id
                """)) {
            select.setLong(1, findingId);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    UUID player = UUID.fromString(rows.getString(1));
                    names.put(player, rows.getString(2));
                    List<MotionFrame> recorded = frames.computeIfAbsent(player, p -> new ArrayList<>());
                    MotionCodec.decode(rows.getBytes(3)).stream()
                            .filter(frame -> frame.atMillis() >= from && frame.atMillis() <= to)
                            .forEach(recorded::add);
                }
            }
        }
        List<Recording> recordings = new ArrayList<>();
        frames.forEach((player, recorded) -> recordings.add(new Recording(player, names.get(player), recorded)));
        return recordings;
    }

    static byte[] encodeCells(int[] cells) {
        Packed.Writer out = new Packed.Writer();
        for (int cell : cells) {
            out.unsigned(cell);
        }
        return Packed.deflate(out.bytes());
    }

    static int[] decodeCells(byte[] packed, int count) {
        Packed.Reader in = new Packed.Reader(Packed.inflate(packed));
        int[] cells = new int[count];
        for (int i = 0; i < count; i++) {
            cells[i] = in.count();
        }
        return cells;
    }

    record LoggedChange(int x, int y, int z, UUID player, String playerName, BlockAction action, String blockData,
                        String previousData, long changedAt) {
    }
}
