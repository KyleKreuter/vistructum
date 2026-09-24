package de.kylekreuter.vistructum.core.store;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class Database implements AutoCloseable {

    private static final String SCHEMA_RESOURCE = "/schema.sql";
    private static final int BUSY_TIMEOUT_MILLIS = 5000;

    private final Connection connection;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "vistructum-db"));

    private Database(Connection connection) {
        this.connection = connection;
    }

    public static Database open(Path file) throws SQLException, IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        SQLiteConfig config = new SQLiteConfig();
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        config.setBusyTimeout(BUSY_TIMEOUT_MILLIS);
        config.enforceForeignKeys(true);
        SQLiteDataSource source = new SQLiteDataSource(config);
        source.setUrl("jdbc:sqlite:" + file.toAbsolutePath());
        Connection connection = source.getConnection();
        try (Statement statement = connection.createStatement()) {
            for (String sql : schema().split(";")) {
                if (!sql.isBlank()) {
                    statement.execute(sql);
                }
            }
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return new Database(connection);
    }

    public <T> CompletableFuture<T> transaction(SqlWork<T> work) {
        Objects.requireNonNull(work, "work");
        return CompletableFuture.supplyAsync(() -> {
            try {
                connection.setAutoCommit(false);
                try {
                    T result = work.run(connection);
                    connection.commit();
                    return result;
                } catch (SQLException | RuntimeException e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }

    private static String schema() throws IOException {
        try (InputStream in = Objects.requireNonNull(Database.class.getResourceAsStream(SCHEMA_RESOURCE), SCHEMA_RESOURCE)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
