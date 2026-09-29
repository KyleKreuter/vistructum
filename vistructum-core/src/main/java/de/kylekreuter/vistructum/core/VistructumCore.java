package de.kylekreuter.vistructum.core;

import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.InferenceMode;
import de.kylekreuter.vistructum.api.Verdict;
import de.kylekreuter.vistructum.api.Vistructum;
import de.kylekreuter.vistructum.core.alert.FindingExporter;
import de.kylekreuter.vistructum.core.alert.FindingHeatmaps;
import de.kylekreuter.vistructum.core.alert.FindingReporter;
import de.kylekreuter.vistructum.core.alert.FindingRetention;
import de.kylekreuter.vistructum.core.alert.FindingStore;
import de.kylekreuter.vistructum.core.evidence.EvidenceKeeper;
import de.kylekreuter.vistructum.core.evidence.EvidenceSettings;
import de.kylekreuter.vistructum.core.evidence.EvidenceStore;
import de.kylekreuter.vistructum.core.inference.FallbackInference;
import de.kylekreuter.vistructum.core.inference.Inference;
import de.kylekreuter.vistructum.core.inference.InferenceSettings;
import de.kylekreuter.vistructum.core.inference.LocalInference;
import de.kylekreuter.vistructum.core.inference.OcclusionEngine;
import de.kylekreuter.vistructum.core.inference.RemoteInference;
import de.kylekreuter.vistructum.core.mask.MaskMonitor;
import de.kylekreuter.vistructum.core.metrics.UsageMetrics;
import de.kylekreuter.vistructum.core.recording.MotionRecorder;
import de.kylekreuter.vistructum.core.recording.MotionStore;
import de.kylekreuter.vistructum.core.scan.DailySchedule;
import de.kylekreuter.vistructum.core.scan.ScanStore;
import de.kylekreuter.vistructum.core.scan.WorldScanner;
import de.kylekreuter.vistructum.core.volume.VolumeSettings;
import de.kylekreuter.vistructum.core.scene.MaskProjector;
import de.kylekreuter.vistructum.core.sidecar.SidecarClient;
import de.kylekreuter.vistructum.core.skin.MojangSkins;
import de.kylekreuter.vistructum.core.skin.SkinCache;
import de.kylekreuter.vistructum.core.skin.SkinStore;
import de.kylekreuter.vistructum.core.store.Database;
import de.kylekreuter.vistructum.core.tracking.BlockChangeListener;
import de.kylekreuter.vistructum.core.tracking.BlockChangeStore;
import de.kylekreuter.vistructum.core.tracking.ClusterSettings;
import de.kylekreuter.vistructum.core.update.UpdateCheck;
import de.kylekreuter.vistructum.core.web.Tokens;
import de.kylekreuter.vistructum.core.web.WebStore;
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
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class VistructumCore extends JavaPlugin {

    private static final double VOLUME_PRISM_FILL = 0.9;
    private static final int VOLUME_MIN_SIDE = 5;
    private static final String EXPORT_FOLDER = "exports";

    private Database database;
    private Inference inference;
    private OcclusionEngine occlusion;
    private MotionRecorder recorder;
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
        InferenceEngine engine = settings.runsLocalModels()
                ? InferenceEngine.start(new ModelFiles(getDataFolder().toPath().resolve("models")), settings.threads(),
                        getLogger())
                : null;
        inference = createInference(settings, engine);
        int threads = settings.threads();
        occlusion = engine != null ? OcclusionEngine.sharing(engine)
                : OcclusionEngine.startingOnDemand(() -> InferenceEngine.start(
                        new ModelFiles(getDataFolder().toPath().resolve("models")), threads, getLogger()));
        scheduleUpdateChecks(settings, engine);

        Duration dedupe = Duration.ofDays(config.getLong("alerts.dedupe-days"));
        FindingReporter reporter = new FindingReporter(mainThread, findings, getLogger(), dedupe, clock);

        getServer().getPluginManager().registerEvents(new BlockChangeListener(changes, clock::millis), this);
        ClusterSettings clusterSettings = new ClusterSettings(Duration.ofMinutes(config.getLong("tracking.ttl-minutes")),
                config.getInt("tracking.link-distance"), Duration.ofSeconds(config.getLong("tracking.quiet-seconds")),
                config.getInt("tracking.min-blocks"), config.getInt("tracking.max-extent"));
        boolean recordingEnabled = config.getBoolean("recording.enabled");
        EvidenceStore evidence = new EvidenceStore(database);
        Consumer<Finding> secureEvidence = finding -> {
        };
        if (recordingEnabled) {
            EvidenceSettings evidenceSettings = new EvidenceSettings(config.getInt("recording.radius"),
                    Duration.ofSeconds(config.getLong("recording.lead-seconds")), config.getInt("recording.margin"));
            recorder = new MotionRecorder(this, new MotionStore(database), clock::millis,
                    clusterSettings.ttl().plus(evidenceSettings.lead()));
            recorder.start();
            EvidenceKeeper keeper = new EvidenceKeeper(mainThread, evidence, recorder, evidenceSettings);
            secureEvidence = finding -> keeper.secure(finding).exceptionally(error -> {
                getLogger().warning("cannot secure evidence for finding #" + finding.id() + ": " + error);
                return false;
            });
        }
        maskMonitor = new MaskMonitor(mainThread, changes, clusterSettings, new MaskProjector(), inference, reporter,
                secureEvidence, clock);
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

        SkinStore skins = new SkinStore(database);
        WebStore web = new WebStore(database, new Tokens());
        Map<Verdict, Duration> keep = new EnumMap<>(Verdict.class);
        retentionDays(config, "retention.reviewed-days", dedupe).ifPresent(days -> keep.put(Verdict.FALSE_ALARM, days));
        retentionDays(config, "retention.confirmed-days", dedupe).ifPresent(days -> keep.put(Verdict.CONFIRMED, days));
        retention = new FindingRetention(mainThread, findings, skins, web, keep, getLogger(), clock);
        retention.start();

        getServer().getServicesManager().register(Vistructum.class,
                new VistructumService(mainThread, changes, findings,
                        new FindingExporter(findings, getDataFolder().toPath().resolve(EXPORT_FOLDER), clock), scans,
                        scanner, inference, new SkinCache(skins, new MojangSkins(), clock),
                        new FindingHeatmaps(findings, occlusion), evidence, web, recordingEnabled, clock), this,
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
        if (recorder != null) {
            recorder.stop();
        }
        if (occlusion != null) {
            occlusion.close();
        }
        if (inference != null) {
            inference.close();
        }
        if (database != null) {
            database.close();
        }
    }

    private Optional<Duration> retentionDays(FileConfiguration config, String key, Duration dedupe) {
        long days = config.getLong(key);
        if (days <= 0) {
            return Optional.empty();
        }
        Duration keep = Duration.ofDays(days);
        if (keep.compareTo(dedupe) < 0) {
            getLogger().warning(key + " is shorter than alerts.dedupe-days, using " + dedupe.toDays() + " days");
            return Optional.of(dedupe);
        }
        return Optional.of(keep);
    }

    private Inference createInference(InferenceSettings settings, InferenceEngine engine) {
        LocalInference local = engine != null ? new LocalInference(engine) : null;
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

    private void scheduleUpdateChecks(InferenceSettings settings, InferenceEngine engine) {
        UpdateCheck check = new UpdateCheck(GitHubReleases.of(settings.repository()),
                getPluginMeta().getVersion(), engine, settings.autoUpdate(), getLogger());
        long period = settings.checkInterval().toSeconds() * 20L;
        getServer().getScheduler().runTaskTimerAsynchronously(this, check, 0L, period);
    }
}
