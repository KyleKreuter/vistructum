package de.kylekreuter.vistructum.core.mask;

import com.google.gson.JsonObject;
import de.kylekreuter.vistructum.api.FindingCandidate;
import de.kylekreuter.vistructum.api.Preview;
import de.kylekreuter.vistructum.api.Source;
import de.kylekreuter.vistructum.core.MainThread;
import de.kylekreuter.vistructum.core.alert.DetectedCandidate;
import de.kylekreuter.vistructum.core.alert.FindingReporter;
import de.kylekreuter.vistructum.core.alert.ModelInput;
import de.kylekreuter.vistructum.core.alert.PreviewCrop;
import de.kylekreuter.vistructum.core.inference.Inference;
import de.kylekreuter.vistructum.core.scene.Axis;
import de.kylekreuter.vistructum.core.scene.BlockPos;
import de.kylekreuter.vistructum.core.scene.BreakClassifier;
import de.kylekreuter.vistructum.core.scene.MaskProjector;
import de.kylekreuter.vistructum.core.scene.Projection;
import de.kylekreuter.vistructum.core.tracking.BlockChangeStore;
import de.kylekreuter.vistructum.core.tracking.Cluster;
import de.kylekreuter.vistructum.core.tracking.ClusterSettings;
import de.kylekreuter.vistructum.inference.Detection;
import de.kylekreuter.vistructum.inference.InferResult;
import de.kylekreuter.vistructum.inference.ModelKind;
import de.kylekreuter.vistructum.inference.SurfaceScene;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitTask;

import java.time.Clock;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public final class MaskMonitor {

    private static final long WARN_INTERVAL_MILLIS = 60_000;

    private final MainThread mainThread;
    private final BlockChangeStore changes;
    private final ClusterSettings settings;
    private final MaskProjector projector;
    private final Inference inference;
    private final FindingReporter reporter;
    private final Clock clock;
    private final Logger logger;
    private final AtomicLong lastWarning = new AtomicLong();
    private BukkitTask task;

    public MaskMonitor(MainThread mainThread, BlockChangeStore changes, ClusterSettings settings, MaskProjector projector,
                       Inference inference, FindingReporter reporter, Clock clock) {
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.changes = Objects.requireNonNull(changes, "changes");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.projector = Objects.requireNonNull(projector, "projector");
        this.inference = Objects.requireNonNull(inference, "inference");
        this.reporter = Objects.requireNonNull(reporter, "reporter");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = mainThread.plugin().getLogger();
    }

    public void start(long periodTicks) {
        task = Bukkit.getScheduler().runTaskTimer(mainThread.plugin(), this::poll, periodTicks, periodTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
    }

    private void poll() {
        changes.takeReady(settings, clock.millis())
                .thenAccept(clusters -> clusters.stream().filter(cluster -> !cluster.oversized()).forEach(this::check))
                .exceptionally(this::warn);
    }

    private void check(Cluster cluster) {
        Map<BlockPos, String> broken = cluster.broken();
        if (broken.isEmpty()) {
            checkCarved(cluster, noneCarved());
            return;
        }
        mainThread.supply(() -> carvedPerAxis(cluster, broken))
                .thenAccept(carved -> checkCarved(cluster, carved))
                .exceptionally(this::warn);
    }

    private void checkCarved(Cluster cluster, Map<Axis, Set<BlockPos>> carvedPerAxis) {
        Set<BlockPos> placed = cluster.placed();
        for (Axis axis : Axis.values()) {
            Set<BlockPos> carved = carvedPerAxis.get(axis);
            Set<BlockPos> positions = new HashSet<>(placed);
            positions.addAll(carved);
            if (positions.size() < settings.minBlocks()) {
                continue;
            }
            Set<UUID> players = cluster.responsibleFor(placed, carved);
            projector.project(axis, positions).ifPresent(projection ->
                    inference.infer(ModelKind.MASK, projection.scene(), context(cluster, projection, positions.size()))
                            .thenCompose(result -> reporter.reportAll(candidates(cluster, players, projection, result)))
                            .exceptionally(this::warn));
        }
    }

    private static Map<Axis, Set<BlockPos>> noneCarved() {
        Map<Axis, Set<BlockPos>> carved = new EnumMap<>(Axis.class);
        for (Axis axis : Axis.values()) {
            carved.put(axis, Set.of());
        }
        return carved;
    }

    private static Map<Axis, Set<BlockPos>> carvedPerAxis(Cluster cluster, Map<BlockPos, String> broken) {
        World world = Bukkit.getWorld(cluster.world());
        if (world == null) {
            return noneCarved();
        }
        Map<Axis, Set<BlockPos>> carved = new EnumMap<>(Axis.class);
        for (Axis axis : Axis.values()) {
            carved.put(axis, BreakClassifier.carved(axis, broken, cluster.positions(), pos -> sample(world, pos)));
        }
        return carved;
    }

    private static BreakClassifier.Sample sample(World world, BlockPos pos) {
        if (!world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) {
            return BreakClassifier.Sample.UNLOADED;
        }
        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        return new BreakClassifier.Sample(true, block.getType().isSolid(), block.getType().name());
    }

    private static List<DetectedCandidate> candidates(Cluster cluster, Set<UUID> players, Projection projection,
                                                     InferResult result) {
        if (!result.flagged()) {
            return List.of();
        }
        SurfaceScene scene = projection.scene();
        return result.detections().stream().map(d -> candidate(cluster, players, projection, scene, result, d)).toList();
    }

    private static DetectedCandidate candidate(Cluster cluster, Set<UUID> players, Projection projection,
                                               SurfaceScene scene, InferResult result, Detection detection) {
        FindingCandidate candidate = new FindingCandidate(Source.MASK, cluster.world(),
                projection.toWorld(detection.top(), detection.left(), detection.bottom(), detection.right()),
                detection.score(), detection.votes(), players, "Achse " + projection.axis(),
                result.modelVersion(), PreviewCrop.ofMask(scene.modified(), scene.width(), scene.height(), detection.top(),
                detection.left(), detection.bottom(), detection.right()));
        return new DetectedCandidate(candidate, ModelInput.of(ModelKind.MASK, scene, detection));
    }

    private static JsonObject context(Cluster cluster, Projection projection, int blocks) {
        JsonObject context = new JsonObject();
        context.addProperty("source", Source.MASK.modelKind());
        context.addProperty("world", cluster.world());
        context.addProperty("axis", projection.axis().name());
        context.addProperty("min_x", cluster.min().x());
        context.addProperty("min_y", cluster.min().y());
        context.addProperty("min_z", cluster.min().z());
        context.addProperty("blocks", blocks);
        return context;
    }

    private <T> T warn(Throwable error) {
        long now = System.currentTimeMillis();
        long last = lastWarning.get();
        if (now - last > WARN_INTERVAL_MILLIS && lastWarning.compareAndSet(last, now)) {
            logger.warning("mask check failed: " + error);
        }
        return null;
    }
}
