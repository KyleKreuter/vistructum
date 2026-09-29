package de.kylekreuter.vistructum.core.evidence;

import de.kylekreuter.vistructum.api.BlockBox;
import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.core.MainThread;
import de.kylekreuter.vistructum.core.recording.MotionRecorder;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public final class EvidenceKeeper {

    static final long MAX_CELLS = 1L << 21;

    private final MainThread mainThread;
    private final EvidenceStore store;
    private final MotionRecorder recorder;
    private final EvidenceSettings settings;
    private final Logger logger;

    public EvidenceKeeper(MainThread mainThread, EvidenceStore store, MotionRecorder recorder,
                          EvidenceSettings settings) {
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.store = Objects.requireNonNull(store, "store");
        this.recorder = Objects.requireNonNull(recorder, "recorder");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.logger = mainThread.plugin().getLogger();
    }

    public CompletableFuture<Boolean> secure(Finding finding) {
        return mainThread.supply(() -> {
            recorder.flushAll();
            return snapshot(finding);
        }).thenCompose(snapshot -> snapshot
                .map(current -> store.secure(finding.id(), finding.world(), finding.box(), current, settings,
                        finding.createdAt()))
                .orElseGet(() -> CompletableFuture.completedFuture(false)));
    }

    private Optional<VolumeSnapshot> snapshot(Finding finding) {
        World world = Bukkit.getWorld(finding.world());
        if (world == null) {
            return Optional.empty();
        }
        BlockBox region = region(finding.box(), settings.margin(), world.getMinHeight(), world.getMaxHeight() - 1);
        if (VolumeSnapshot.volume(region) > MAX_CELLS) {
            logger.warning("finding #" + finding.id() + " is too large to secure evidence for");
            return Optional.empty();
        }
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
        return new BlockBox(box.minX() - margin, Math.max(minY, box.minY() - margin), box.minZ() - margin,
                box.maxX() + margin, Math.min(maxY, box.maxY() + margin), box.maxZ() + margin);
    }
}
