package de.kylekreuter.vistructum.core.history;

import de.kylekreuter.vistructum.api.BlockAction;
import de.kylekreuter.vistructum.api.BlockBox;
import de.kylekreuter.vistructum.core.scene.BlockPos;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class Builders {

    private Builders() {
    }

    public static Set<UUID> of(List<HistoryEntry> entries, BlockBox box) {
        Map<BlockPos, HistoryEntry> latest = new HashMap<>();
        entries.stream()
                .filter(entry -> box.contains(entry.x(), entry.y(), entry.z()))
                .sorted(Comparator.comparingLong(HistoryEntry::changedAt))
                .forEach(entry -> latest.put(new BlockPos(entry.x(), entry.y(), entry.z()), entry));
        return latest.values().stream()
                .filter(entry -> entry.action() == BlockAction.PLACE)
                .map(HistoryEntry::player)
                .flatMap(Optional::stream)
                .collect(Collectors.toUnmodifiableSet());
    }
}
