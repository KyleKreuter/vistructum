package de.kylekreuter.vistructum.core.web;

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
    private static final String REVOKE = """
            UPDATE evidence_shares SET revoked_at = ?, revoked_by = ? WHERE finding_id = ? AND revoked_at IS NULL
            """;
    private static final String SHARED_FINDING = """
            SELECT s.finding_id FROM evidence_shares s JOIN findings f ON f.id = s.finding_id
            WHERE s.token_hash = ? AND s.revoked_at IS NULL AND f.verdict = ?
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
        String token = tokens.next();
        return database.transaction(connection -> {
            try (PreparedStatement select = connection.prepareStatement(SHAREABLE)) {
                select.setLong(1, findingId);
                select.setString(2, Verdict.CONFIRMED.name());
                try (ResultSet rows = select.executeQuery()) {
                    if (!rows.getBoolean(1)) {
                        return Optional.empty();
                    }
                }
            }
            revoke(connection, findingId, actor, now);
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO evidence_shares (finding_id, token_hash, created_at, created_by) VALUES (?, ?, ?, ?)")) {
                insert.setLong(1, findingId);
                insert.setString(2, Tokens.hash(token));
                insert.setLong(3, now.toEpochMilli());
                insert.setString(4, actor);
                insert.executeUpdate();
            }
            return Optional.of(token);
        });
    }

    public CompletableFuture<Boolean> unshare(long findingId, String actor, Instant now) {
        return database.transaction(connection -> revoke(connection, findingId, actor, now) > 0);
    }

    public CompletableFuture<Optional<Long>> sharedFinding(String shareToken) {
        return database.transaction(connection -> {
            try (PreparedStatement select = connection.prepareStatement(SHARED_FINDING)) {
                select.setString(1, Tokens.hash(shareToken));
                select.setString(2, Verdict.CONFIRMED.name());
                try (ResultSet rows = select.executeQuery()) {
                    return rows.next() ? Optional.of(rows.getLong(1)) : Optional.empty();
                }
            }
        });
    }

    public CompletableFuture<Optional<Instant>> sharedSince(long findingId) {
        return database.transaction(connection -> {
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT created_at FROM evidence_shares WHERE finding_id = ? AND revoked_at IS NULL")) {
                select.setLong(1, findingId);
                try (ResultSet rows = select.executeQuery()) {
                    return rows.next() ? Optional.of(Instant.ofEpochMilli(rows.getLong(1))) : Optional.empty();
                }
            }
        });
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

    private static int revoke(Connection connection, long findingId, String actor, Instant now) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(REVOKE)) {
            update.setLong(1, now.toEpochMilli());
            update.setString(2, actor);
            update.setLong(3, findingId);
            return update.executeUpdate();
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
