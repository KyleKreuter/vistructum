package de.kylekreuter.vistructum.core.store;

import java.nio.file.Path;

public final class TestDatabase {

    private TestDatabase() {
    }

    public static Database open(Path directory) {
        try {
            return Database.open(directory.resolve("test.db"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
