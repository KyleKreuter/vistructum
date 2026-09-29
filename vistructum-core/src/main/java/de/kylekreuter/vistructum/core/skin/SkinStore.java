package de.kylekreuter.vistructum.core.skin;

import de.kylekreuter.vistructum.api.PlayerSkin;
import de.kylekreuter.vistructum.core.store.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SkinStore {

    private final Database database;

    public SkinStore(Database database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    public CompletableFuture<Optional<StoredSkin>> find(UUID player) {
        return database.transaction(connection -> {
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT name, png, slim, fetched_at FROM player_skins WHERE player = ?")) {
                select.setString(1, player.toString());
                try (ResultSet row = select.executeQuery()) {
                    if (!row.next()) {
                        return Optional.empty();
                    }
                    Optional<String> name = Optional.ofNullable(row.getString("name"));
                    byte[] png = row.getBytes("png");
                    SkinLookup lookup = png == null ? SkinLookup.without(player, name)
                            : SkinLookup.of(player, name, png, row.getBoolean("slim"));
                    return Optional.of(new StoredSkin(lookup, Instant.ofEpochMilli(row.getLong("fetched_at"))));
                }
            }
        });
    }

    public CompletableFuture<Void> save(SkinLookup lookup, Instant fetchedAt) {
        return database.transaction(connection -> {
            try (PreparedStatement upsert = connection.prepareStatement(
                    "INSERT OR REPLACE INTO player_skins (player, name, png, slim, fetched_at) VALUES (?, ?, ?, ?, ?)")) {
                upsert.setString(1, lookup.player().toString());
                upsert.setString(2, lookup.name().orElse(null));
                upsert.setBytes(3, lookup.skin().map(PlayerSkin::png).orElse(null));
                upsert.setBoolean(4, lookup.skin().map(PlayerSkin::slim).orElse(false));
                upsert.setLong(5, fetchedAt.toEpochMilli());
                upsert.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Integer> deleteUnreferenced() {
        return database.transaction(connection -> {
            try (PreparedStatement delete = connection.prepareStatement("""
                    DELETE FROM player_skins
                    WHERE NOT EXISTS (SELECT 1 FROM findings WHERE instr(findings.players, player_skins.player) > 0)
                    AND NOT EXISTS (SELECT 1 FROM finding_recordings r WHERE r.player = player_skins.player)
                    AND NOT EXISTS (SELECT 1 FROM finding_changes c WHERE c.player = player_skins.player)
                    """)) {
                return delete.executeUpdate();
            }
        });
    }
}
