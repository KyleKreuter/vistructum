package de.kylekreuter.vistructum.core.history;

import de.kylekreuter.vistructum.api.BlockAction;
import de.kylekreuter.vistructum.api.BlockBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record RollbackPlan(List<BlockRestore> restores, int skipped) {

    static final String AIR = "minecraft:air";

    public RollbackPlan {
        restores = List.copyOf(restores);
    }

    public static RollbackPlan of(List<HistoryEntry> entries, BlockBox box, Set<UUID> players) {
        Map<Position, Track> tracks = new LinkedHashMap<>();
        entries.stream()
                .filter(entry -> box.contains(entry.x(), entry.y(), entry.z()))
                .sorted(Comparator.comparingLong(HistoryEntry::changedAt))
                .forEach(entry -> tracks.computeIfAbsent(new Position(entry.x(), entry.y(), entry.z()),
                        ignored -> new Track()).add(entry, entry.player().filter(players::contains).isPresent()));
        List<BlockRestore> restores = new ArrayList<>();
        int skipped = 0;
        for (Map.Entry<Position, Track> track : tracks.entrySet()) {
            Track changes = track.getValue();
            if (changes.restored == null) {
                continue;
            }
            if (!changes.lastByPlayers) {
                skipped++;
                continue;
            }
            if (!changes.state.equals(changes.restored)) {
                Position position = track.getKey();
                restores.add(new BlockRestore(position.x, position.y, position.z, changes.state, changes.restored));
            }
        }
        return new RollbackPlan(restores, skipped);
    }

    private record Position(int x, int y, int z) {
    }

    private static final class Track {

        private String state;
        private String restored;
        private boolean lastByPlayers;

        void add(HistoryEntry entry, boolean byPlayers) {
            boolean placed = entry.action() == BlockAction.PLACE;
            String before = placed ? (state == null ? AIR : state) : entry.blockData();
            if (byPlayers && restored == null) {
                restored = before;
            }
            state = placed ? entry.blockData() : AIR;
            lastByPlayers = byPlayers;
        }
    }
}
