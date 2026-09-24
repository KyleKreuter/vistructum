package de.kylekreuter.vistructum.core.mask;

import com.google.gson.JsonObject;
import de.kylekreuter.vistructum.core.alert.DetectionReporter;
import de.kylekreuter.vistructum.core.alert.Finding;
import de.kylekreuter.vistructum.core.scene.MaskProjector;
import de.kylekreuter.vistructum.core.scene.Projection;
import de.kylekreuter.vistructum.core.sidecar.Detection;
import de.kylekreuter.vistructum.core.sidecar.SidecarClient;
import de.kylekreuter.vistructum.core.tracking.Cluster;
import de.kylekreuter.vistructum.core.tracking.ModificationTracker;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Checks each quiet cluster of recent building activity with the mask model, once per projection axis, so symbols
 * built on the ground and on walls are both seen.
 */
public final class MaskMonitor {

    private static final String KIND = "mask";
    private static final long WARN_INTERVAL_MILLIS = 60_000;

    private final Plugin plugin;
    private final ModificationTracker tracker;
    private final MaskProjector projector;
    private final SidecarClient client;
    private final DetectionReporter reporter;
    private final AtomicLong lastWarning = new AtomicLong();
    private final AtomicInteger checked = new AtomicInteger();
    private BukkitTask task;

    public MaskMonitor(Plugin plugin, ModificationTracker tracker, MaskProjector projector, SidecarClient client,
                       DetectionReporter reporter) {
        this.plugin = plugin;
        this.tracker = tracker;
        this.projector = projector;
        this.client = client;
        this.reporter = reporter;
    }

    public void start(long periodTicks) {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::poll, periodTicks, periodTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
    }

    public int checkedProjections() {
        return checked.get();
    }

    void poll() {
        for (Cluster cluster : tracker.pollReady(System.currentTimeMillis())) {
            World world = Bukkit.getWorld(cluster.world());
            if (cluster.oversized() || world == null) {
                continue;
            }
            for (Projection projection : projector.project(cluster.positions())) {
                client.infer(KIND, projection.scene(), context(world, cluster, projection))
                        .whenComplete((result, error) -> {
                            checked.incrementAndGet();
                            if (error != null) {
                                warn(error);
                            } else if (result.flagged()) {
                                onMainThread(() -> result.detections().forEach(d -> report(world, cluster, projection, d)));
                            }
                        });
            }
        }
    }

    private void report(World world, Cluster cluster, Projection projection, Detection detection) {
        reporter.report(new Finding(KIND, world.getName(),
                projection.toWorld(detection.top(), detection.left(), detection.bottom(), detection.right()),
                detection.score(), detection.votes(), cluster.players(), "Achse " + projection.axis(), Instant.now()));
    }

    private static JsonObject context(World world, Cluster cluster, Projection projection) {
        JsonObject context = new JsonObject();
        context.addProperty("source", KIND);
        context.addProperty("world", world.getName());
        context.addProperty("axis", projection.axis().name());
        context.addProperty("min_x", cluster.min().x());
        context.addProperty("min_y", cluster.min().y());
        context.addProperty("min_z", cluster.min().z());
        context.addProperty("blocks", cluster.positions().size());
        return context;
    }

    private void onMainThread(Runnable action) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, action);
        }
    }

    private void warn(Throwable error) {
        long now = System.currentTimeMillis();
        long last = lastWarning.get();
        if (now - last > WARN_INTERVAL_MILLIS && lastWarning.compareAndSet(last, now)) {
            plugin.getLogger().warning("mask check failed (sidecar unreachable?): " + error);
        }
    }
}
