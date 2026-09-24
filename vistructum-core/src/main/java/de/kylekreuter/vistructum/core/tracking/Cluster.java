package de.kylekreuter.vistructum.core.tracking;

import de.kylekreuter.vistructum.core.scene.BlockPos;

import java.util.Set;
import java.util.UUID;

public record Cluster(String world, Set<BlockPos> positions, Set<UUID> players, BlockPos min, BlockPos max,
                      long newestChangeMillis, boolean oversized) {

    public Cluster {
        positions = Set.copyOf(positions);
        players = Set.copyOf(players);
    }
}
