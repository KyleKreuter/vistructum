package de.kylekreuter.vistructum.ui.integration.punishment;

import litebans.api.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

final class LiteBansLog implements PunishmentLog {

    private static final Map<PunishmentKind, String> TABLES = Map.of(PunishmentKind.BAN, "{bans}",
            PunishmentKind.MUTE, "{mutes}", PunishmentKind.WARN, "{warnings}", PunishmentKind.KICK, "{kicks}");

    private final Executor queries;
    private final Clock clock;

    LiteBansLog(Executor queries, Clock clock) {
        this.queries = Objects.requireNonNull(queries, "queries");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String source() {
        return "LiteBans";
    }

    @Override
    public CompletableFuture<List<Punishment>> history(UUID player) {
        return CompletableFuture.supplyAsync(() -> {
            Instant now = clock.instant();
            List<Punishment> punishments = new ArrayList<>();
            for (PunishmentKind kind : PunishmentKind.values()) {
                punishments.addAll(query(kind, player, now));
            }
            return punishments;
        }, queries);
    }

    private static List<Punishment> query(PunishmentKind kind, UUID player, Instant now) {
        String sql = "SELECT reason, banned_by_uuid, banned_by_name, time, until, active FROM " + TABLES.get(kind)
                + " WHERE uuid = ? ORDER BY time DESC";
        try (PreparedStatement statement = Database.get().prepareStatement(sql)) {
            statement.setMaxRows(LIMIT);
            statement.setString(1, player.toString());
            try (ResultSet rows = statement.executeQuery()) {
                List<Punishment> punishments = new ArrayList<>();
                while (rows.next()) {
                    punishments.add(punishment(kind, rows, now));
                }
                return punishments;
            }
        } catch (SQLException e) {
            throw new CompletionException(e);
        }
    }

    private static Punishment punishment(PunishmentKind kind, ResultSet row, Instant now) throws SQLException {
        long until = row.getLong("until");
        Optional<Instant> expiresAt = kind == PunishmentKind.KICK || until <= 0 ? Optional.empty()
                : Optional.of(Instant.ofEpochMilli(until));
        boolean active = kind != PunishmentKind.KICK && row.getBoolean("active")
                && expiresAt.map(now::isBefore).orElse(true);
        return new Punishment(kind, Optional.ofNullable(row.getString("reason")),
                Optional.ofNullable(row.getString("banned_by_name")), uuid(row.getString("banned_by_uuid")),
                Optional.of(Instant.ofEpochMilli(row.getLong("time"))), expiresAt, active);
    }

    static Optional<UUID> uuid(String text) {
        if (text == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(text));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
