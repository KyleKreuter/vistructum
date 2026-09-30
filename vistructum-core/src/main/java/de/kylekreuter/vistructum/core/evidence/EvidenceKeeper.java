package de.kylekreuter.vistructum.core.evidence;

import de.kylekreuter.vistructum.api.BlockBox;
import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.Source;
import de.kylekreuter.vistructum.core.MainThread;
import de.kylekreuter.vistructum.core.history.BlockHistory;
import de.kylekreuter.vistructum.core.recording.MotionRecorder;
import de.kylekreuter.vistructum.inference.Contract;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class EvidenceKeeper {

    static final int MAX_EXTENT = Contract.GRID;

    private final MainThread mainThread;
    private final EvidenceStore store;
    private final Optional<MotionRecorder> recorder;
    private final Optional<BlockHistory> history;
    private final EvidenceSettings settings;

    public EvidenceKeeper(MainThread mainThread, EvidenceStore store, Optional<MotionRecorder> recorder,
                          Optional<BlockHistory> history, EvidenceSettings settings) {
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.store = Objects.requireNonNull(store, "store");
        this.recorder = Objects.requireNonNull(recorder, "recorder");
        this.history = Objects.requireNonNull(history, "history");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public CompletableFuture<Boolean> secure(Finding finding) {
        if (recorder.isPresent() && finding.source() == Source.MASK) {
            return secureTracked(finding, recorder.get());
        }
        return secureFromHistory(finding);
    }

    public CompletableFuture<Boolean> secureFromHistory(Finding finding) {
        return history.map(blocks -> secureHistory(finding, blocks))
                .orElseGet(() -> CompletableFuture.completedFuture(false));
    }

    private CompletableFuture<Boolean> secureTracked(Finding finding, MotionRecorder motion) {
        return mainThread.supply(() -> {
            motion.flushAll();
            return snapshot(finding);
        }).thenCompose(snapshot -> snapshot
                .map(current -> store.secure(finding.id(), finding.world(), finding.box(), current, settings,
                        finding.createdAt()))
                .orElseGet(() -> CompletableFuture.completedFuture(false)));
    }

    private CompletableFuture<Boolean> secureHistory(Finding finding, BlockHistory blocks) {
        return mainThread.supply(() -> snapshot(finding)).thenCompose(snapshot -> snapshot
                .map(current -> blocks.lookup(finding.world(), current.region(), finding.createdAt())
                        .thenCompose(entries -> store.secureHistory(finding.id(), current, entries, settings.lead(),
                                finding.createdAt())))
                .orElseGet(() -> CompletableFuture.completedFuture(false)));
    }

    private Optional<VolumeSnapshot> snapshot(Finding finding) {
        World world = Bukkit.getWorld(finding.world());
        if (world == null) {
            return Optional.empty();
        }
        BlockBox region = region(finding.box(), settings.margin(), world.getMinHeight(), world.getMaxHeight() - 1);
        VolumeSnapshot.Builder current = new VolumeSnapshot.Builder(region);
        int index = 0;
        for (int y = region.minY(); y <= region.maxY(); y++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                for (int x = region.minX(); x <= region.maxX(); x++) {
                    current.set(index++, world.getBlockAt(x, y, z).getBlockData().getAsString());
                }
            }
        }
        return Optional.of(current.build());
    }

    static BlockBox region(BlockBox box, int margin, int minY, int maxY) {
        int[] x = clamp(box.minX() - margin, box.maxX() + margin, Integer.MIN_VALUE, Integer.MAX_VALUE);
        int[] y = clamp(box.minY() - margin, box.maxY() + margin, minY, maxY);
        int[] z = clamp(box.minZ() - margin, box.maxZ() + margin, Integer.MIN_VALUE, Integer.MAX_VALUE);
        return new BlockBox(x[0], y[0], z[0], x[1], y[1], z[1]);
    }

    private static int[] clamp(int from, int to, int lowest, int highest) {
        if (to - from + 1 > MAX_EXTENT) {
            from = Math.floorDiv(from + to, 2) - MAX_EXTENT / 2 + 1;
            to = from + MAX_EXTENT - 1;
        }
        return new int[]{Math.max(lowest, from), Math.min(highest, to)};
    }
}
