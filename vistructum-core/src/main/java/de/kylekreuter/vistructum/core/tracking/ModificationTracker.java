package de.kylekreuter.vistructum.core.tracking;

import de.kylekreuter.vistructum.core.scene.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks recent block placements/breaks and groups them into clusters of building activity.
 *
 * <p>Not thread-safe: by contract only the server main thread calls this class, matching the thread
 * that dispatches Bukkit block events.
 */
public final class ModificationTracker {

    private static final int MAX_TRACKED_POSITIONS = 200_000;

    private final TrackerSettings settings;
    private final Map<UUID, WorldState> worlds = new HashMap<>();
    private final LinkedHashMap<Key, PositionRecord> recent = new LinkedHashMap<>(1024, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, PositionRecord> eldest) {
            if (size() > MAX_TRACKED_POSITIONS) {
                forget(eldest.getKey());
                return true;
            }
            return false;
        }
    };

    public ModificationTracker(TrackerSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /** Records a placement or break at {@code (x,y,z)}. Re-recording a position refreshes its time and player set. */
    public void record(UUID world, int x, int y, int z, UUID player, long nowMillis) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(player, "player");
        Key key = new Key(world, new BlockPos(x, y, z));
        PositionRecord rec = recent.remove(key);
        if (rec == null) {
            rec = new PositionRecord(key.pos());
            worlds.computeIfAbsent(world, w -> new WorldState()).byPos.put(key.pos(), rec);
        }
        rec.lastChangeMillis = nowMillis;
        rec.players.add(player);
        recent.put(key, rec);
    }

    /**
     * Evicts expired changes, clusters what remains, and returns clusters that are quiet, meet
     * {@code minBlocks} and have a change newer than their last report.
     */
    public List<Cluster> pollReady(long nowMillis) {
        evictExpired(nowMillis);
        long quietMillis = settings.quietPeriod().toMillis();
        List<Cluster> ready = new ArrayList<>();
        for (Group group : buildGroups()) {
            List<PositionRecord> members = group.members();
            if (members.size() < settings.minBlocks()) {
                continue;
            }
            long newest = Long.MIN_VALUE;
            boolean due = false;
            for (PositionRecord r : members) {
                newest = Math.max(newest, r.lastChangeMillis);
                due |= r.lastChangeMillis > r.lastReportedMillis;
            }
            if (!due || nowMillis - newest < quietMillis) {
                continue;
            }
            ready.add(toCluster(group.world(), members, newest));
            for (PositionRecord r : members) {
                r.lastReportedMillis = nowMillis;
            }
        }
        return ready;
    }

    /** Number of positions currently tracked (not yet evicted), across all worlds. For diagnostics/tests. */
    public int trackedPositionCount() {
        return recent.size();
    }

    private void evictExpired(long nowMillis) {
        long ttlMillis = settings.ttl().toMillis();
        Iterator<Map.Entry<Key, PositionRecord>> it = recent.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Key, PositionRecord> e = it.next();
            if (nowMillis - e.getValue().lastChangeMillis <= ttlMillis) {
                break; // recent is kept oldest-first, so nothing after this is expired either
            }
            it.remove();
            forget(e.getKey());
        }
    }

    private void forget(Key key) {
        WorldState ws = worlds.get(key.world());
        if (ws == null) {
            return;
        }
        ws.byPos.remove(key.pos());
        if (ws.byPos.isEmpty()) {
            worlds.remove(key.world());
        }
    }

    /** Connected components per world, under the Chebyshev link-distance rule. O(n * (2d+1)^3) hash lookups. */
    private List<Group> buildGroups() {
        int d = settings.linkDistance();
        List<Group> groups = new ArrayList<>();
        for (Map.Entry<UUID, WorldState> we : worlds.entrySet()) {
            Map<BlockPos, PositionRecord> byPos = we.getValue().byPos;
            if (byPos.isEmpty()) {
                continue;
            }
            for (PositionRecord r : byPos.values()) {
                r.ufParent = r;
            }
            for (PositionRecord r : byPos.values()) {
                int x = r.pos.x(), y = r.pos.y(), z = r.pos.z();
                for (int dx = -d; dx <= d; dx++) {
                    for (int dy = -d; dy <= d; dy++) {
                        for (int dz = -d; dz <= d; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) {
                                continue;
                            }
                            PositionRecord neighbor = byPos.get(new BlockPos(x + dx, y + dy, z + dz));
                            if (neighbor != null) {
                                union(r, neighbor);
                            }
                        }
                    }
                }
            }
            Map<PositionRecord, List<PositionRecord>> byRoot = new HashMap<>();
            for (PositionRecord r : byPos.values()) {
                byRoot.computeIfAbsent(find(r), root -> new ArrayList<>()).add(r);
            }
            for (List<PositionRecord> members : byRoot.values()) {
                groups.add(new Group(we.getKey(), members));
            }
        }
        return groups;
    }

    private Cluster toCluster(UUID world, List<PositionRecord> members, long newestChangeMillis) {
        Set<BlockPos> positions = new HashSet<>(members.size());
        Set<UUID> players = new HashSet<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (PositionRecord r : members) {
            positions.add(r.pos);
            players.addAll(r.players);
            minX = Math.min(minX, r.pos.x());
            minY = Math.min(minY, r.pos.y());
            minZ = Math.min(minZ, r.pos.z());
            maxX = Math.max(maxX, r.pos.x());
            maxY = Math.max(maxY, r.pos.y());
            maxZ = Math.max(maxZ, r.pos.z());
        }
        BlockPos min = new BlockPos(minX, minY, minZ);
        BlockPos max = new BlockPos(maxX, maxY, maxZ);
        boolean oversized = (maxX - minX + 1) > settings.maxExtent()
                || (maxY - minY + 1) > settings.maxExtent()
                || (maxZ - minZ + 1) > settings.maxExtent();
        return new Cluster(world, positions, players, min, max, newestChangeMillis, oversized);
    }

    private static PositionRecord find(PositionRecord r) {
        PositionRecord root = r;
        while (root.ufParent != root) {
            root = root.ufParent;
        }
        while (r.ufParent != root) {
            PositionRecord next = r.ufParent;
            r.ufParent = root;
            r = next;
        }
        return root;
    }

    private static void union(PositionRecord a, PositionRecord b) {
        PositionRecord ra = find(a);
        PositionRecord rb = find(b);
        if (ra != rb) {
            ra.ufParent = rb;
        }
    }

    private record Key(UUID world, BlockPos pos) {
    }

    private record Group(UUID world, List<PositionRecord> members) {
    }

    private static final class WorldState {
        final Map<BlockPos, PositionRecord> byPos = new HashMap<>();
    }

    /** Mutable per-position bookkeeping; union-find state (ufParent) is scratch, reset on every {@link #buildGroups()}. */
    private static final class PositionRecord {
        final BlockPos pos;
        final Set<UUID> players = new HashSet<>();
        long lastChangeMillis;
        long lastReportedMillis = Long.MIN_VALUE;
        PositionRecord ufParent = this;

        PositionRecord(BlockPos pos) {
            this.pos = pos;
        }
    }
}
