package de.kylekreuter.vistructum.core.alert;

import de.kylekreuter.vistructum.api.BlockVolume;
import de.kylekreuter.vistructum.api.FindingTerrain;
import de.kylekreuter.vistructum.core.store.Database;
import de.kylekreuter.vistructum.core.store.Packed;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class TerrainStore {

    private static final String INSERT = """
            INSERT INTO finding_terrain (finding_id, min_x, min_y, min_z, size_x, size_y, size_z, palette, cells,
                scene_x, scene_z)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT = """
            SELECT min_x, min_y, min_z, size_x, size_y, size_z, palette, cells, scene_x, scene_z
            FROM finding_terrain WHERE finding_id = ?
            """;
    private static final String PALETTE_SEPARATOR = "\n";

    private final Database database;

    public TerrainStore(Database database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    public CompletableFuture<Boolean> hasTerrain(long findingId) {
        return database.transaction(connection -> {
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT EXISTS (SELECT 1 FROM finding_terrain WHERE finding_id = ?)")) {
                select.setLong(1, findingId);
                try (ResultSet rows = select.executeQuery()) {
                    return rows.getBoolean(1);
                }
            }
        });
    }

    public CompletableFuture<Optional<FindingTerrain>> terrain(long findingId) {
        return database.transaction(connection -> {
            try (PreparedStatement select = connection.prepareStatement(SELECT)) {
                select.setLong(1, findingId);
                try (ResultSet rows = select.executeQuery()) {
                    if (!rows.next()) {
                        return Optional.empty();
                    }
                    int sizeX = rows.getInt(4);
                    int sizeY = rows.getInt(5);
                    int sizeZ = rows.getInt(6);
                    BlockVolume blocks = new BlockVolume(rows.getInt(1), rows.getInt(2), rows.getInt(3), sizeX, sizeY,
                            sizeZ, Arrays.asList(rows.getString(7).split(PALETTE_SEPARATOR, -1)),
                            Packed.unpackCells(rows.getBytes(8), sizeX * sizeY * sizeZ));
                    int sceneX = rows.getInt(9);
                    Optional<FindingTerrain.SceneOrigin> origin = rows.wasNull() ? Optional.empty()
                            : Optional.of(new FindingTerrain.SceneOrigin(sceneX, rows.getInt(10)));
                    return Optional.of(new FindingTerrain(blocks, origin));
                }
            }
        });
    }

    static PackedTerrain pack(FindingTerrain terrain) {
        return new PackedTerrain(terrain, Packed.packCells(terrain.blocks().cells()));
    }

    static void insert(Connection connection, long findingId, PackedTerrain packed) throws SQLException {
        BlockVolume blocks = packed.terrain().blocks();
        try (PreparedStatement insert = connection.prepareStatement(INSERT)) {
            insert.setLong(1, findingId);
            insert.setInt(2, blocks.minX());
            insert.setInt(3, blocks.minY());
            insert.setInt(4, blocks.minZ());
            insert.setInt(5, blocks.sizeX());
            insert.setInt(6, blocks.sizeY());
            insert.setInt(7, blocks.sizeZ());
            insert.setString(8, String.join(PALETTE_SEPARATOR, blocks.palette()));
            insert.setBytes(9, packed.cells());
            Optional<FindingTerrain.SceneOrigin> origin = packed.terrain().sceneOrigin();
            if (origin.isPresent()) {
                insert.setInt(10, origin.get().x());
                insert.setInt(11, origin.get().z());
            } else {
                insert.setNull(10, Types.INTEGER);
                insert.setNull(11, Types.INTEGER);
            }
            insert.executeUpdate();
        }
    }

    record PackedTerrain(FindingTerrain terrain, byte[] cells) {
    }
}
