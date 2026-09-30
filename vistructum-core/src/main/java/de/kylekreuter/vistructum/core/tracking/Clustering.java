package de.kylekreuter.vistructum.core.tracking;

import de.kylekreuter.vistructum.core.scene.BlockPos;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class Clustering {

    public static final int CELL_SHIFT = 4;

    private Clustering() {
    }

    public static List<Cluster> ready(Collection<WorldPosition> settled, Collection<WorldPosition> vanished,
                                      CellLoader cells, ClusterSettings settings, long nowMillis) throws SQLException {
        Map<String, Area> areas = new HashMap<>();
        Map<String, List<BlockPos>> vanishedPerWorld = new HashMap<>();
        for (WorldPosition position : vanished) {
            vanishedPerWorld.computeIfAbsent(position.world(), world -> new ArrayList<>()).add(position.pos());
        }
        List<WorldPosition> seeds = new ArrayList<>(settled);
        for (Map.Entry<String, List<BlockPos>> world : vanishedPerWorld.entrySet()) {
            Area area = areas.computeIfAbsent(world.getKey(), name -> new Area(name, cells, settings, nowMillis));
            for (BlockPos pos : area.splitBy(world.getValue())) {
                seeds.add(new WorldPosition(world.getKey(), pos));
            }
        }
        List<Cluster> ready = new ArrayList<>();
        for (WorldPosition seed : seeds) {
            Area area = areas.computeIfAbsent(seed.world(), world -> new Area(world, cells, settings, nowMillis));
            Optional<Cluster> cluster = area.explore(seed.pos());
            if (cluster.isPresent()) {
                ready.add(cluster.get());
            }
        }
        return ready;
    }

    private static long cellKey(int cellX, int cellZ) {
        return ((long) cellX << 32) | (cellZ & 0xFFFF_FFFFL);
    }

    private static List<BlockPos> around(BlockPos pos, int link) {
        List<BlockPos> around = new ArrayList<>((2 * link + 1) * (2 * link + 1) * (2 * link + 1) - 1);
        for (int dx = -link; dx <= link; dx++) {
            for (int dy = -link; dy <= link; dy++) {
                for (int dz = -link; dz <= link; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        around.add(new BlockPos(pos.x() + dx, pos.y() + dy, pos.z() + dz));
                    }
                }
            }
        }
        return around;
    }

    private static final class Area {

        private final String world;
        private final CellLoader cells;
        private final ClusterSettings settings;
        private final long nowMillis;
        private final Set<Long> summarizedCells = new HashSet<>();
        private final Set<Long> detailedCells = new HashSet<>();
        private final Map<BlockPos, PositionSummary> summaries = new HashMap<>();
        private final Map<BlockPos, Position> details = new HashMap<>();
        private final Map<BlockPos, Integer> explored = new HashMap<>();
        private int explorations;

        private Area(String world, CellLoader cells, ClusterSettings settings, long nowMillis) {
            this.world = world;
            this.cells = cells;
            this.settings = settings;
            this.nowMillis = nowMillis;
        }

        private List<BlockPos> splitBy(List<BlockPos> vanished) throws SQLException {
            int link = settings.linkDistance();
            Set<BlockPos> remaining = new HashSet<>(vanished);
            List<BlockPos> seeds = new ArrayList<>();
            for (BlockPos start : vanished) {
                if (!remaining.remove(start)) {
                    continue;
                }
                ArrayDeque<BlockPos> group = new ArrayDeque<>(List.of(start));
                Set<BlockPos> neighbors = new HashSet<>();
                while (!group.isEmpty()) {
                    BlockPos gone = group.poll();
                    summarizeAround(gone);
                    for (BlockPos near : around(gone, link)) {
                        if (remaining.remove(near)) {
                            group.add(near);
                        } else if (summaries.containsKey(near)) {
                            neighbors.add(near);
                        }
                    }
                }
                seeds.addAll(representatives(neighbors, link));
            }
            return seeds;
        }

        private static List<BlockPos> representatives(Set<BlockPos> neighbors, int link) {
            Set<BlockPos> unassigned = new HashSet<>(neighbors);
            List<BlockPos> representatives = new ArrayList<>();
            for (BlockPos start : neighbors) {
                if (!unassigned.remove(start)) {
                    continue;
                }
                representatives.add(start);
                ArrayDeque<BlockPos> queue = new ArrayDeque<>(List.of(start));
                while (!queue.isEmpty()) {
                    for (BlockPos near : around(queue.poll(), link)) {
                        if (unassigned.remove(near)) {
                            queue.add(near);
                        }
                    }
                }
            }
            return representatives.size() > 1 ? representatives : List.of();
        }

        private Optional<Cluster> explore(BlockPos seed) throws SQLException {
            if (explored.containsKey(seed)) {
                return Optional.empty();
            }
            summarizeAround(seed);
            PositionSummary first = summaries.get(seed);
            if (first == null) {
                return Optional.empty();
            }
            long quietMillis = settings.quietPeriod().toMillis();
            int link = settings.linkDistance();
            int exploration = ++explorations;
            List<BlockPos> members = new ArrayList<>();
            ArrayDeque<PositionSummary> queue = new ArrayDeque<>();
            explored.put(seed, exploration);
            queue.add(first);
            long newest = Long.MIN_VALUE;
            boolean unreported = false;
            while (!queue.isEmpty()) {
                PositionSummary position = queue.poll();
                if (nowMillis - position.newest() < quietMillis) {
                    return Optional.empty();
                }
                newest = Math.max(newest, position.newest());
                unreported |= position.unreported();
                members.add(position.pos());
                summarizeAround(position.pos());
                for (BlockPos near : around(position.pos(), link)) {
                    PositionSummary neighbor = summaries.get(near);
                    if (neighbor == null) {
                        continue;
                    }
                    Integer earlier = explored.putIfAbsent(near, exploration);
                    if (earlier == null) {
                        queue.add(neighbor);
                    } else if (earlier != exploration) {
                        return Optional.empty();
                    }
                }
            }
            if (members.size() < settings.minBlocks() || !unreported) {
                return Optional.empty();
            }
            return Optional.of(cluster(members, newest));
        }

        private Cluster cluster(List<BlockPos> members, long newest) throws SQLException {
            int maxExtent = settings.maxExtent();
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            Map<BlockPos, ChangedBlock> blocks = new HashMap<>(members.size());
            for (BlockPos pos : members) {
                minX = Math.min(minX, pos.x());
                minY = Math.min(minY, pos.y());
                minZ = Math.min(minZ, pos.z());
                maxX = Math.max(maxX, pos.x());
                maxY = Math.max(maxY, pos.y());
                maxZ = Math.max(maxZ, pos.z());
                detail(pos);
                Position position = details.get(pos);
                blocks.put(pos, new ChangedBlock(position.kind, position.material, position.placers, position.breakers));
            }
            boolean oversized = maxX - minX + 1 > maxExtent || maxY - minY + 1 > maxExtent || maxZ - minZ + 1 > maxExtent;
            return new Cluster(world, blocks, new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), newest,
                    oversized);
        }

        private void summarizeAround(BlockPos pos) throws SQLException {
            int link = settings.linkDistance();
            for (int cellX = (pos.x() - link) >> CELL_SHIFT; cellX <= (pos.x() + link) >> CELL_SHIFT; cellX++) {
                for (int cellZ = (pos.z() - link) >> CELL_SHIFT; cellZ <= (pos.z() + link) >> CELL_SHIFT; cellZ++) {
                    if (summarizedCells.add(cellKey(cellX, cellZ))) {
                        for (PositionSummary summary : cells.summaries(world, cellX, cellZ)) {
                            summaries.put(summary.pos(), summary);
                        }
                    }
                }
            }
        }

        private void detail(BlockPos pos) throws SQLException {
            int cellX = pos.x() >> CELL_SHIFT;
            int cellZ = pos.z() >> CELL_SHIFT;
            if (!detailedCells.add(cellKey(cellX, cellZ))) {
                return;
            }
            for (BlockChange change : cells.changes(world, cellX, cellZ)) {
                Position position = details.computeIfAbsent(change.pos(), changed -> new Position());
                (change.kind() == ChangeKind.PLACE ? position.placers : position.breakers).add(change.player());
                if (change.changedAt() >= position.newest) {
                    position.newest = change.changedAt();
                    position.kind = change.kind();
                    position.material = change.material();
                }
            }
        }
    }

    private static final class Position {

        private final Set<UUID> placers = new HashSet<>();
        private final Set<UUID> breakers = new HashSet<>();
        private long newest = Long.MIN_VALUE;
        private ChangeKind kind = ChangeKind.PLACE;
        private String material = "";
    }
}
