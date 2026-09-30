package de.kylekreuter.vistructum.core.tracking;

import de.kylekreuter.vistructum.core.scene.BlockPos;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record InMemoryCells(List<BlockChange> all) implements CellLoader {

    @Override
    public List<PositionSummary> summaries(String world, int cellX, int cellZ) {
        Map<BlockPos, long[]> newestAndReported = new LinkedHashMap<>();
        for (BlockChange change : changes(world, cellX, cellZ)) {
            long[] values = newestAndReported.computeIfAbsent(change.pos(), pos -> new long[]{Long.MIN_VALUE, Long.MIN_VALUE});
            values[0] = Math.max(values[0], change.changedAt());
            values[1] = Math.max(values[1], change.reportedAt());
        }
        return newestAndReported.entrySet().stream()
                .map(entry -> new PositionSummary(entry.getKey(), entry.getValue()[0], entry.getValue()[0] > entry.getValue()[1]))
                .toList();
    }

    @Override
    public List<BlockChange> changes(String world, int cellX, int cellZ) {
        return all.stream()
                .filter(change -> change.world().equals(world)
                        && change.pos().x() >> Clustering.CELL_SHIFT == cellX
                        && change.pos().z() >> Clustering.CELL_SHIFT == cellZ)
                .sorted(Comparator.comparingLong(BlockChange::changedAt))
                .toList();
    }
}
