package de.kylekreuter.vistructum.core.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseTest {

    @TempDir
    Path directory;

    @Test
    void failedTransactionRollsBack() throws Exception {
        try (Database database = TestDatabase.open(directory)) {
            ExecutionException failure = assertThrows(ExecutionException.class, () -> database.transaction(connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("INSERT INTO daily_scans (day) VALUES ('2026-09-24')");
                    statement.executeUpdate("INSERT INTO daily_scans (day) VALUES ('2026-09-24')");
                }
                return null;
            }).get());
            assertTrue(failure.getCause() instanceof SQLException);
            assertEquals(0, count(database));
        }
    }

    @Test
    void reopeningKeepsRows() throws Exception {
        try (Database database = TestDatabase.open(directory)) {
            database.transaction(connection -> {
                try (Statement statement = connection.createStatement()) {
                    return statement.executeUpdate("INSERT INTO daily_scans (day) VALUES ('2026-09-24')");
                }
            }).get();
        }
        try (Database database = TestDatabase.open(directory)) {
            assertEquals(1, count(database));
        }
    }

    private static int count(Database database) throws Exception {
        return database.transaction(connection -> {
            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT count(*) FROM daily_scans")) {
                return rows.getInt(1);
            }
        }).get();
    }
}
