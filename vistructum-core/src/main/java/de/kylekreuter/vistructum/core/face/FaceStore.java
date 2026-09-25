package de.kylekreuter.vistructum.core.face;

import de.kylekreuter.vistructum.api.PlayerFace;
import de.kylekreuter.vistructum.core.store.Database;

import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class FaceStore {

    private final Database database;

    public FaceStore(Database database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    public CompletableFuture<Optional<StoredFace>> find(UUID player) {
        return database.transaction(connection -> {
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT name, pixels, fetched_at FROM player_faces WHERE player = ?")) {
                select.setString(1, player.toString());
                try (ResultSet row = select.executeQuery()) {
                    if (!row.next()) {
                        return Optional.empty();
                    }
                    PlayerFace face = new PlayerFace(player, Optional.ofNullable(row.getString("name")),
                            decode(row.getBytes("pixels")));
                    return Optional.of(new StoredFace(face, Instant.ofEpochMilli(row.getLong("fetched_at"))));
                }
            }
        });
    }

    public CompletableFuture<Void> save(PlayerFace face, Instant fetchedAt) {
        return database.transaction(connection -> {
            try (PreparedStatement upsert = connection.prepareStatement(
                    "INSERT OR REPLACE INTO player_faces (player, name, pixels, fetched_at) VALUES (?, ?, ?, ?)")) {
                upsert.setString(1, face.player().toString());
                upsert.setString(2, face.name().orElse(null));
                upsert.setBytes(3, face.hasSkin() ? encode(face.pixels()) : null);
                upsert.setLong(4, fetchedAt.toEpochMilli());
                upsert.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Integer> deleteUnreferenced() {
        return database.transaction(connection -> {
            try (PreparedStatement delete = connection.prepareStatement("""
                    DELETE FROM player_faces
                    WHERE NOT EXISTS (SELECT 1 FROM findings WHERE instr(findings.players, player_faces.player) > 0)
                    """)) {
                return delete.executeUpdate();
            }
        });
    }

    private static byte[] encode(List<Integer> pixels) {
        ByteBuffer buffer = ByteBuffer.allocate(pixels.size() * Integer.BYTES);
        pixels.forEach(buffer::putInt);
        return buffer.array();
    }

    private static List<Integer> decode(byte[] bytes) {
        if (bytes == null) {
            return List.of();
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        List<Integer> pixels = new ArrayList<>(bytes.length / Integer.BYTES);
        while (buffer.hasRemaining()) {
            pixels.add(buffer.getInt());
        }
        return pixels;
    }
}
