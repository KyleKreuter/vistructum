package de.kylekreuter.vistructum.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Blocks around a full scan finding as the world scan read them.
 *
 * <p>The volume covers the finding box plus a horizontal margin, from a few blocks below the lowest surface up to
 * the highest block of the covered columns. Unlike the {@link FindingScene}, which keeps one surface block per
 * column, it holds every block state, so trees, plants and overhangs keep their shape.
 *
 * @param blocks block states of the covered region at scan time
 * @param sceneOrigin world position of the scene cell in row {@code 0} and column {@code 0}, present if the scene
 *                    of the finding is a top-down surface raster aligned with the world axes
 */
public record FindingTerrain(BlockVolume blocks, Optional<SceneOrigin> sceneOrigin) {

    /**
     * Validates the components.
     *
     * @throws NullPointerException if an argument is {@code null}
     */
    public FindingTerrain {
        Objects.requireNonNull(blocks, "blocks");
        Objects.requireNonNull(sceneOrigin, "sceneOrigin");
    }

    /**
     * World position of the first cell of a top-down scene.
     *
     * <p>The scene cell in row {@code r} and column {@code c} covers the block column at {@code x + c} and
     * {@code z + r}.
     *
     * @param x world x coordinate of column {@code 0}
     * @param z world z coordinate of row {@code 0}
     */
    public record SceneOrigin(int x, int z) {
    }
}
