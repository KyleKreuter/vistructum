package de.kylekreuter.vistructum.core.tracking;

import de.kylekreuter.vistructum.core.scene.BlockPos;

import java.util.Set;
import java.util.UUID;

/**
 * A connected group of recent block changes, ready to be sent on for projection.
 *
 * @param oversized true when the bbox exceeds the tracker's {@code maxExtent} on any axis; the caller decides
 *                  whether to still project it, downsample it or drop it
 */
public record Cluster(UUID world, Set<BlockPos> positions, Set<UUID> players, BlockPos min, BlockPos max,
                       long newestChangeMillis, boolean oversized) {

    public Cluster {
        positions = Set.copyOf(positions);
        players = Set.copyOf(players);
    }
}
