package de.kylekreuter.vistructum.core.tracking;

import de.kylekreuter.vistructum.core.scene.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ReferenceTracker {

    private final List<Row> rows = new ArrayList<>();
    private long nextId;

    void record(TrackedChange change) {
        rows.add(new Row(nextId++, change));
    }

    List<Cluster> takeReady(ClusterSettings settings, long nowMillis) {
        rows.removeIf(row -> row.change.changedAt() < nowMillis - settings.ttl().toMillis());
        Map<Key, Row> latest = new HashMap<>();
        Map<Key, Long> reported = new HashMap<>();
        for (Row row : rows) {
            Key key = new Key(row.change.world(), row.change.pos(), row.change.player());
            latest.merge(key, row, (a, b) -> newer(a, b) ? a : b);
            reported.merge(key, row.reportedAt, Math::max);
        }
        List<BlockChange> changes = new ArrayList<>();
        latest.entrySet().stream().sorted(Map.Entry.comparingByValue(ReferenceTracker::compareAge)).forEach(entry -> {
            Key key = entry.getKey();
            Row row = entry.getValue();
            changes.add(new BlockChange(key.world, key.pos, key.player, row.change.kind(),
                    TrackedChange.material(row.change.blockData()), row.change.changedAt(), reported.get(key)));
        });
        List<Cluster> ready = ReferenceClustering.ready(changes, settings, nowMillis);
        for (Cluster cluster : ready) {
            for (Row row : rows) {
                if (row.change.world().equals(cluster.world()) && cluster.blocks().containsKey(row.change.pos())) {
                    row.reportedAt = nowMillis;
                }
            }
        }
        return ready;
    }

    private static boolean newer(Row a, Row b) {
        return compareAge(a, b) > 0;
    }

    private static int compareAge(Row a, Row b) {
        int byTime = Long.compare(a.change.changedAt(), b.change.changedAt());
        return byTime != 0 ? byTime : Long.compare(a.id, b.id);
    }

    private record Key(String world, BlockPos pos, UUID player) {
    }

    private static final class Row {

        private final long id;
        private final TrackedChange change;
        private long reportedAt;

        private Row(long id, TrackedChange change) {
            this.id = id;
            this.change = change;
        }
    }
}
