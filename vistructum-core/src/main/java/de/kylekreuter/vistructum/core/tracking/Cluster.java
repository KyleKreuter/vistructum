package de.kylekreuter.vistructum.core.tracking;

import de.kylekreuter.vistructum.core.scene.BlockPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record Cluster(String world, Map<BlockPos, ChangedBlock> blocks, BlockPos min, BlockPos max,
                      long newestChangeMillis, boolean oversized) {

    public Cluster {
        blocks = Map.copyOf(blocks);
    }

    public Set<BlockPos> positions() {
        return blocks.keySet();
    }

    public Set<BlockPos> placed() {
        return positionsOf(ChangeKind.PLACE);
    }

    public Map<BlockPos, String> broken() {
        Map<BlockPos, String> broken = new HashMap<>();
        blocks.forEach((pos, block) -> {
            if (block.kind() == ChangeKind.BREAK) {
                broken.put(pos, block.material());
            }
        });
        return broken;
    }

    public Set<UUID> players() {
        Set<UUID> players = new HashSet<>();
        blocks.values().forEach(block -> {
            players.addAll(block.placers());
            players.addAll(block.breakers());
        });
        return players;
    }

    public Set<UUID> responsibleFor(Set<BlockPos> placed, Set<BlockPos> carved) {
        Set<UUID> players = new HashSet<>();
        placed.forEach(pos -> players.addAll(blocks.get(pos).placers()));
        carved.forEach(pos -> players.addAll(blocks.get(pos).breakers()));
        return players;
    }

    private Set<BlockPos> positionsOf(ChangeKind kind) {
        Set<BlockPos> positions = new HashSet<>();
        blocks.forEach((pos, block) -> {
            if (block.kind() == kind) {
                positions.add(pos);
            }
        });
        return positions;
    }
}
