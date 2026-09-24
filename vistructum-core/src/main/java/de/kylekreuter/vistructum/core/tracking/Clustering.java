package de.kylekreuter.vistructum.core.tracking;

import de.kylekreuter.vistructum.core.scene.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Clustering {

    private Clustering() {
    }

    public static List<Cluster> ready(Collection<BlockChange> changes, ClusterSettings settings, long nowMillis) {
        long ttlMillis = settings.ttl().toMillis();
        long quietMillis = settings.quietPeriod().toMillis();
        Map<String, Map<BlockPos, Position>> worlds = new LinkedHashMap<>();
        for (BlockChange change : changes) {
            if (nowMillis - change.changedAt() > ttlMillis) {
                continue;
            }
            Position position = worlds.computeIfAbsent(change.world(), w -> new LinkedHashMap<>())
                    .computeIfAbsent(change.pos(), Position::new);
            position.players.add(change.player());
            position.newest = Math.max(position.newest, change.changedAt());
            position.unreported |= change.unreported();
        }
        List<Cluster> ready = new ArrayList<>();
        for (Map.Entry<String, Map<BlockPos, Position>> world : worlds.entrySet()) {
            for (List<Position> members : components(world.getValue(), settings.linkDistance())) {
                if (members.size() < settings.minBlocks()) {
                    continue;
                }
                long newest = members.stream().mapToLong(p -> p.newest).max().orElseThrow();
                boolean unreported = members.stream().anyMatch(p -> p.unreported);
                if (unreported && nowMillis - newest >= quietMillis) {
                    ready.add(toCluster(world.getKey(), members, newest, settings.maxExtent()));
                }
            }
        }
        return ready;
    }

    private static Collection<List<Position>> components(Map<BlockPos, Position> byPos, int linkDistance) {
        for (Position position : byPos.values()) {
            position.parent = position;
        }
        for (Position position : byPos.values()) {
            BlockPos pos = position.pos;
            for (int dx = -linkDistance; dx <= linkDistance; dx++) {
                for (int dy = -linkDistance; dy <= linkDistance; dy++) {
                    for (int dz = -linkDistance; dz <= linkDistance; dz++) {
                        Position neighbor = byPos.get(new BlockPos(pos.x() + dx, pos.y() + dy, pos.z() + dz));
                        if (neighbor != null && neighbor != position) {
                            union(position, neighbor);
                        }
                    }
                }
            }
        }
        Map<Position, List<Position>> byRoot = new LinkedHashMap<>();
        for (Position position : byPos.values()) {
            byRoot.computeIfAbsent(root(position), r -> new ArrayList<>()).add(position);
        }
        return byRoot.values();
    }

    private static Cluster toCluster(String world, List<Position> members, long newest, int maxExtent) {
        Set<BlockPos> positions = new HashSet<>(members.size());
        Set<UUID> players = new HashSet<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Position member : members) {
            BlockPos pos = member.pos;
            positions.add(pos);
            players.addAll(member.players);
            minX = Math.min(minX, pos.x());
            minY = Math.min(minY, pos.y());
            minZ = Math.min(minZ, pos.z());
            maxX = Math.max(maxX, pos.x());
            maxY = Math.max(maxY, pos.y());
            maxZ = Math.max(maxZ, pos.z());
        }
        boolean oversized = maxX - minX + 1 > maxExtent || maxY - minY + 1 > maxExtent || maxZ - minZ + 1 > maxExtent;
        return new Cluster(world, positions, players, new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ),
                newest, oversized);
    }

    private static Position root(Position position) {
        Position root = position;
        while (root.parent != root) {
            root = root.parent;
        }
        while (position.parent != root) {
            Position next = position.parent;
            position.parent = root;
            position = next;
        }
        return root;
    }

    private static void union(Position a, Position b) {
        Position rootA = root(a);
        Position rootB = root(b);
        if (rootA != rootB) {
            rootA.parent = rootB;
        }
    }

    private static final class Position {

        private final BlockPos pos;
        private final Set<UUID> players = new HashSet<>();
        private long newest = Long.MIN_VALUE;
        private boolean unreported;
        private Position parent = this;

        private Position(BlockPos pos) {
            this.pos = pos;
        }
    }
}
