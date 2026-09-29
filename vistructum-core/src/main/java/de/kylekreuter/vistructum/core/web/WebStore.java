package de.kylekreuter.vistructum.core.web;

import de.kylekreuter.vistructum.api.ActivityKind;
import de.kylekreuter.vistructum.api.EvidenceShare;
import de.kylekreuter.vistructum.api.IssuedSession;
import de.kylekreuter.vistructum.api.Verdict;
import de.kylekreuter.vistructum.api.WebAccess;
import de.kylekreuter.vistructum.api.WebSession;
import de.kylekreuter.vistructum.core.store.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class WebStore {

    static final Duration LOGIN_VALIDITY = Duration.ofMinutes(WebAccess.LOGIN_MINUTES);
    static final Duration SESSION_VALIDITY = Duration.ofHours(WebAccess.SESSION_HOURS);

    private static final String SHAREABLE = """
            SELECT EXISTS (SELECT 1 FROM findings f JOIN finding_evidence e ON e.finding_id = f.id
                WHERE f.id = ? AND f.verdict = ?)
            """;
    private static final String ACTIVATE = """
            INSERT INTO evidence_shares (finding_id, token, active, shared_since) VALUES (?, ?, 1, ?)
            ON CONFLICT (finding_id) DO UPDATE SET active = 1, shared_since = excluded.shared_since
            """;

    private final Database database;
    private final Tokens tokens;

    public WebStore(Database database, Tokens tokens) {
        this.database = Objects.requireNonNull(database, "database");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
    }

    public CompletableFuture<String> issueLogin(UUID player, String playerName, Instant now) {
        String token = tokens.next();
        return database.transaction(connection -> {
            insertGrant(connection, "web_logins", token, player, playerName, now.plus(LOGIN_VALIDITY));
            return token;
        });
    }

    public CompletableFuture<Optional<IssuedSession>> redeemLogin(String loginToken, Instant now) {
        String sessionToken = tokens.next();
        return database.transaction(connection -> {
            Optional<WebSession> login = grant(connection, "web_logins", loginToken, now);
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM web_logins WHERE token_hash = ?")) {
                delete.setString(1, Tokens.hash(loginToken));
                delete.executeUpdate();
            }
            if (login.isEmpty()) {
                return Optional.empty();
            }
            Instant expiresAt = now.plus(SESSION_VALIDITY);
            insertGrant(connection, "web_sessions", sessionToken, login.get().player(), login.get().playerName(),
                    expiresAt);
            return Optional.of(new IssuedSession(sessionToken,
                    new WebSession(login.get().player(), login.get().playerName(), expiresAt)));
        });
    }

    public CompletableFuture<Optional<WebSession>> session(String sessionToken, Instant now) {
        return database.transaction(connection -> grant(connection, "web_sessions", sessionToken, now));
    }

    public CompletableFuture<Void> endSession(String sessionToken) {
        return database.transaction(connection -> {
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM web_sessions WHERE token_hash = ?")) {
                delete.setString(1, Tokens.hash(sessionToken));
                delete.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Optional<String>> share(long findingId, String actor, Instant now) {
        String issued = tokens.next();
        return database.transaction(connection -> {
            Optional<EvidenceShare> active = link(connection, findingId, true);
            if (active.isPresent()) {
                return Optional.of(active.get().token());
            }
            if (!shareable(connection, findingId)) {
                return Optional.empty();
            }
            String token = link(connection, findingId, false).map(EvidenceShare::token).orElse(issued);
            try (PreparedStatement upsert = connection.prepareStatement(ACTIVATE)) {
                upsert.setLong(1, findingId);
                upsert.setString(2, token);
                upsert.setLong(3, now.toEpochMilli());
                upsert.executeUpdate();
            }
            record(connection, findingId, actor, now, ActivityKind.SHARED);
            return Optional.of(token);
        });
    }

    public CompletableFuture<Boolean> unshare(long findingId, String actor, Instant now) {
        return database.transaction(connection -> {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE evidence_shares SET active = 0 WHERE finding_id = ? AND active = 1")) {
                update.setLong(1, findingId);
                if (update.executeUpdate() == 0) {
                    return false;
                }
            }
            record(connection, findingId, actor, now, ActivityKind.UNSHARED);
            return true;
        });
    }

    public CompletableFuture<Optional<Long>> sharedFinding(String shareToken) {
        return database.transaction(connection -> {
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT finding_id FROM evidence_shares WHERE token = ? AND active = 1")) {
                select.setString(1, shareToken);
                try (ResultSet rows = select.executeQuery()) {
                    return rows.next() ? Optional.of(rows.getLong(1)) : Optional.empty();
                }
            }
        });
    }

    public CompletableFuture<Optional<EvidenceShare>> shared(long findingId) {
        return database.transaction(connection -> link(connection, findingId, true));
    }

    public CompletableFuture<Integer> deleteExpired(Instant now) {
        return database.transaction(connection -> {
            int deleted = 0;
            for (String table : new String[]{"web_logins", "web_sessions"}) {
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM " + table + " WHERE expires_at <= ?")) {
                    delete.setLong(1, now.toEpochMilli());
                    deleted += delete.executeUpdate();
                }
            }
            return deleted;
        });
    }

    private static boolean shareable(Connection connection, long findingId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(SHAREABLE)) {
            select.setLong(1, findingId);
            select.setString(2, Verdict.CONFIRMED.name());
            try (ResultSet rows = select.executeQuery()) {
                return rows.getBoolean(1);
            }
        }
    }

    private static Optional<EvidenceShare> link(Connection connection, long findingId, boolean onlyActive)
            throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT token, shared_since, active FROM evidence_shares WHERE finding_id = ?")) {
            select.setLong(1, findingId);
            try (ResultSet rows = select.executeQuery()) {
                if (!rows.next() || onlyActive && !rows.getBoolean(3)) {
                    return Optional.empty();
                }
                return Optional.of(new EvidenceShare(rows.getString(1), Instant.ofEpochMilli(rows.getLong(2))));
            }
        }
    }

    private static void record(Connection connection, long findingId, String actor, Instant now, ActivityKind kind)
            throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO share_events (finding_id, at, actor, kind) VALUES (?, ?, ?, ?)")) {
            insert.setLong(1, findingId);
            insert.setLong(2, now.toEpochMilli());
            insert.setString(3, actor);
            insert.setString(4, kind.name());
            insert.executeUpdate();
        }
    }

    private static void insertGrant(Connection connection, String table, String token, UUID player, String playerName,
                                    Instant expiresAt) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + table
                + " (token_hash, player, player_name, expires_at) VALUES (?, ?, ?, ?)")) {
            insert.setString(1, Tokens.hash(token));
            insert.setString(2, player.toString());
            insert.setString(3, playerName);
            insert.setLong(4, expiresAt.toEpochMilli());
            insert.executeUpdate();
        }
    }

    private static Optional<WebSession> grant(Connection connection, String table, String token, Instant now)
            throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT player, player_name, expires_at FROM "
                + table + " WHERE token_hash = ? AND expires_at > ?")) {
            select.setString(1, Tokens.hash(token));
            select.setLong(2, now.toEpochMilli());
            try (ResultSet rows = select.executeQuery()) {
                return rows.next() ? Optional.of(new WebSession(UUID.fromString(rows.getString(1)), rows.getString(2),
                        Instant.ofEpochMilli(rows.getLong(3)))) : Optional.empty();
            }
        }
    }
}
