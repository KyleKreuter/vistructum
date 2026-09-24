package de.kylekreuter.vistructum.core.mask;

import com.google.gson.JsonObject;
import de.kylekreuter.vistructum.api.Preview;
import de.kylekreuter.vistructum.api.Source;
import de.kylekreuter.vistructum.core.MainThread;
import de.kylekreuter.vistructum.core.alert.FindingDraft;
import de.kylekreuter.vistructum.core.alert.FindingReporter;
import de.kylekreuter.vistructum.core.alert.PreviewCrop;
import de.kylekreuter.vistructum.core.scene.MaskProjector;
import de.kylekreuter.vistructum.core.scene.Projection;
import de.kylekreuter.vistructum.core.scene.SurfaceScene;
import de.kylekreuter.vistructum.core.sidecar.Detection;
import de.kylekreuter.vistructum.core.sidecar.InferResult;
import de.kylekreuter.vistructum.core.sidecar.SidecarClient;
import de.kylekreuter.vistructum.core.tracking.BlockChangeStore;
import de.kylekreuter.vistructum.core.tracking.Cluster;
import de.kylekreuter.vistructum.core.tracking.ClusterSettings;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public final class MaskMonitor {

    private static final long WARN_INTERVAL_MILLIS = 60_000;

    private final MainThread mainThread;
    private final BlockChangeStore changes;
    private final ClusterSettings settings;
    private final MaskProjector projector;
    private final SidecarClient client;
    private final FindingReporter reporter;
    private final Clock clock;
    private final Logger logger;
    private final AtomicLong lastWarning = new AtomicLong();
    private BukkitTask task;

    public MaskMonitor(MainThread mainThread, BlockChangeStore changes, ClusterSettings settings, MaskProjector projector,
                       SidecarClient client, FindingReporter reporter, Clock clock) {
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.changes = Objects.requireNonNull(changes, "changes");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.projector = Objects.requireNonNull(projector, "projector");
        this.client = Objects.requireNonNull(client, "client");
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
        for (Projection projection : projector.project(cluster.positions())) {
            client.infer(Source.MASK.modelKind(), projection.scene(), context(cluster, projection))
                    .thenCompose(result -> reporter.reportAll(drafts(cluster, projection, result)))
                    .exceptionally(this::warn);
        }
    }

    private static List<FindingDraft> drafts(Cluster cluster, Projection projection, InferResult result) {
        if (!result.flagged()) {
            return List.of();
        }
        SurfaceScene scene = projection.scene();
        return result.detections().stream().map(d -> draft(cluster, projection, scene, result, d)).toList();
    }

    private static FindingDraft draft(Cluster cluster, Projection projection, SurfaceScene scene, InferResult result,
                                      Detection detection) {
        return new FindingDraft(Source.MASK, cluster.world(),
                projection.toWorld(detection.top(), detection.left(), detection.bottom(), detection.right()),
                detection.score(), detection.votes(), cluster.players(), "Achse " + projection.axis(),
                result.modelVersion(), PreviewCrop.ofMask(scene.modified(), scene.width(), scene.height(), detection.top(),
                detection.left(), detection.bottom(), detection.right()));
    }

    private static JsonObject context(Cluster cluster, Projection projection) {
        JsonObject context = new JsonObject();
        context.addProperty("source", Source.MASK.modelKind());
        context.addProperty("world", cluster.world());
        context.addProperty("axis", projection.axis().name());
        context.addProperty("min_x", cluster.min().x());
        context.addProperty("min_y", cluster.min().y());
        context.addProperty("min_z", cluster.min().z());
        context.addProperty("blocks", cluster.positions().size());
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
