package de.kylekreuter.vistructum.core;

import de.kylekreuter.vistructum.api.InferenceMode;
import de.kylekreuter.vistructum.api.Vistructum;
import de.kylekreuter.vistructum.core.alert.FindingReporter;
import de.kylekreuter.vistructum.core.alert.FindingRetention;
import de.kylekreuter.vistructum.core.alert.FindingStore;
import de.kylekreuter.vistructum.core.face.FaceCache;
import de.kylekreuter.vistructum.core.face.FaceStore;
import de.kylekreuter.vistructum.core.face.MojangFaces;
import de.kylekreuter.vistructum.core.inference.FallbackInference;
import de.kylekreuter.vistructum.core.inference.Inference;
import de.kylekreuter.vistructum.core.inference.InferenceSettings;
import de.kylekreuter.vistructum.core.inference.LocalInference;
import de.kylekreuter.vistructum.core.inference.RemoteInference;
import de.kylekreuter.vistructum.core.mask.MaskMonitor;
import de.kylekreuter.vistructum.core.metrics.UsageMetrics;
import de.kylekreuter.vistructum.core.scan.DailySchedule;
import de.kylekreuter.vistructum.core.scan.ScanStore;
import de.kylekreuter.vistructum.core.scan.WorldScanner;
import de.kylekreuter.vistructum.core.volume.VolumeSettings;
import de.kylekreuter.vistructum.core.scene.MaskProjector;
import de.kylekreuter.vistructum.core.sidecar.SidecarClient;
import de.kylekreuter.vistructum.core.store.Database;
import de.kylekreuter.vistructum.core.tracking.BlockChangeListener;
import de.kylekreuter.vistructum.core.tracking.BlockChangeStore;
import de.kylekreuter.vistructum.core.tracking.ClusterSettings;
import de.kylekreuter.vistructum.inference.GitHubReleases;
import de.kylekreuter.vistructum.inference.InferenceEngine;
import de.kylekreuter.vistructum.inference.ModelFiles;
import org.bstats.bukkit.Metrics;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalTime;
import java.util.logging.Level;

public final class VistructumCore extends JavaPlugin {

    private static final double VOLUME_PRISM_FILL = 0.9;
    private static final int VOLUME_MIN_SIDE = 5;

    private Database database;
    private Inference inference;
    private MaskMonitor maskMonitor;
    private WorldScanner scanner;
    private DailySchedule schedule;
    private FindingRetention retention;
    private Metrics metrics;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        Clock clock = Clock.systemDefaultZone();
        MainThread mainThread = new MainThread(this);

        try {
            database = Database.open(getDataFolder().toPath().resolve(config.getString("database.file")));
        } catch (SQLException | IOException e) {
            getLogger().log(Level.SEVERE, "cannot open the database, disabling", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        BlockChangeStore changes = new BlockChangeStore(database);
        FindingStore findings = new FindingStore(database);
        ScanStore scans = new ScanStore(database);

        InferenceSettings settings;
        try {
            settings = InferenceSettings.from(config, System.getenv());
        } catch (IllegalArgumentException | NullPointerException e) {
            getLogger().severe("invalid inference configuration, disabling: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        inference = createInference(settings);

        Duration dedupe = Duration.ofDays(config.getLong("alerts.dedupe-days"));
        FindingReporter reporter = new FindingReporter(mainThread, findings, getLogger(), dedupe, clock);

        getServer().getPluginManager().registerEvents(new BlockChangeListener(changes, clock::millis), this);
        ClusterSettings clusterSettings = new ClusterSettings(Duration.ofMinutes(config.getLong("tracking.ttl-minutes")),
                config.getInt("tracking.link-distance"), Duration.ofSeconds(config.getLong("tracking.quiet-seconds")),
                config.getInt("tracking.min-blocks"), config.getInt("tracking.max-extent"));
        maskMonitor = new MaskMonitor(mainThread, changes, clusterSettings, new MaskProjector(), inference, reporter, clock);
        maskMonitor.start(20L * config.getLong("tracking.poll-seconds"));

        VolumeSettings volume = new VolumeSettings(config.getDouble("scan.volume.filler-share"),
                clusterSettings.linkDistance(), clusterSettings.minBlocks(), clusterSettings.maxExtent(),
                VOLUME_PRISM_FILL, VOLUME_MIN_SIDE);
        scanner = new WorldScanner(mainThread, scans, inference, reporter, config.getInt("scan.chunks-per-tick"),
                config.getInt("scan.workers"), volume, clock);
        scanner.start();
        if (config.getBoolean("scan.enabled")) {
            schedule = new DailySchedule(mainThread, scans, scanner, LocalTime.parse(config.getString("scan.daily-at")),
                    config.getStringList("scan.worlds"), clock);
            schedule.start();
        }

        FaceStore faces = new FaceStore(database);
        long reviewedDays = config.getLong("retention.reviewed-days");
        if (reviewedDays > 0) {
            Duration keepReviewed = Duration.ofDays(reviewedDays);
            if (keepReviewed.compareTo(dedupe) < 0) {
                getLogger().warning("retention.reviewed-days is shorter than alerts.dedupe-days, using "
                        + dedupe.toDays() + " days");
                keepReviewed = dedupe;
            }
            retention = new FindingRetention(mainThread, findings, faces, keepReviewed, getLogger(), clock);
            retention.start();
        }

        getServer().getServicesManager().register(Vistructum.class,
                new VistructumService(mainThread, changes, findings, scans, scanner, inference,
                        new FaceCache(faces, new MojangFaces(), clock), clock), this,
                ServicePriority.Normal);

        metrics = UsageMetrics.start(this, settings, config.getBoolean("scan.enabled"),
                config.getStringList("scan.worlds").size());
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (metrics != null) {
            metrics.shutdown();
        }
        if (retention != null) {
            retention.stop();
        }
        if (schedule != null) {
            schedule.stop();
        }
        if (scanner != null) {
            scanner.close();
        }
        if (maskMonitor != null) {
            maskMonitor.stop();
        }
        if (inference != null) {
            inference.close();
        }
        if (database != null) {
            database.close();
        }
    }

    private Inference createInference(InferenceSettings settings) {
        LocalInference local = null;
        if (settings.runsLocalModels()) {
            InferenceEngine engine = InferenceEngine.start(new ModelFiles(getDataFolder().toPath().resolve("models")),
                    settings.threads(), getLogger());
            local = new LocalInference(engine);
            if (settings.autoUpdate()) {
                scheduleModelUpdates(engine, settings);
            }
        }
        if (settings.mode() == InferenceMode.LOCAL) {
            getLogger().info("inference runs locally");
            return local;
        }
        RemoteInference remote = new RemoteInference(new SidecarClient(settings.sidecarHost(), settings.sidecarPort(),
                settings.connectTimeout(), settings.requestTimeout()));
        getLogger().info("inference runs on the sidecar at " + settings.sidecarHost() + ":" + settings.sidecarPort()
                + (local != null ? ", local fallback enabled" : ""));
        return local != null ? new FallbackInference(remote, local, getLogger()) : remote;
    }

    private void scheduleModelUpdates(InferenceEngine engine, InferenceSettings settings) {
        GitHubReleases releases = GitHubReleases.of(settings.repository());
        long period = settings.checkInterval().toSeconds() * 20L;
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> engine.update(releases)
                .whenComplete((installed, error) -> {
                    if (error != null) {
                        getLogger().warning("model update check failed: " + error.getMessage());
                    }
                }), 0L, period);
    }
}
