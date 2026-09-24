package de.kylekreuter.vistructum.core;

import de.kylekreuter.vistructum.api.ModelContract;
import de.kylekreuter.vistructum.core.alert.DetectionReporter;
import de.kylekreuter.vistructum.core.mask.MaskMonitor;
import de.kylekreuter.vistructum.core.scan.DailySchedule;
import de.kylekreuter.vistructum.core.scan.WorldScanner;
import de.kylekreuter.vistructum.core.scene.MaskProjector;
import de.kylekreuter.vistructum.core.sidecar.SidecarClient;
import de.kylekreuter.vistructum.core.tracking.BlockChangeListener;
import de.kylekreuter.vistructum.core.tracking.ModificationTracker;
import de.kylekreuter.vistructum.core.tracking.TrackerSettings;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Map;
import java.util.Objects;

public final class VistructumCore extends JavaPlugin {

    private SidecarClient client;
    private DetectionReporter reporter;
    private MaskMonitor maskMonitor;
    private WorldScanner scanner;
    private DailySchedule schedule;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();

        String host = Objects.requireNonNullElse(System.getenv("SIDECAR_HOST"), config.getString("sidecar.host"));
        int port = System.getenv("SIDECAR_PORT") != null ? Integer.parseInt(System.getenv("SIDECAR_PORT"))
                : config.getInt("sidecar.port");
        client = new SidecarClient(host, port, Duration.ofSeconds(config.getLong("sidecar.connect-timeout-seconds")),
                Duration.ofSeconds(config.getLong("sidecar.request-timeout-seconds")));
        checkModels();

        reporter = new DetectionReporter(getLogger(), config.getString("alerts.permission"),
                getDataFolder().toPath().resolve(config.getString("alerts.log-file")),
                Duration.ofDays(config.getLong("alerts.dedupe-days")));
        reporter.load();

        ModificationTracker tracker = new ModificationTracker(new TrackerSettings(
                Duration.ofMinutes(config.getLong("tracking.ttl-minutes")), config.getInt("tracking.link-distance"),
                Duration.ofSeconds(config.getLong("tracking.quiet-seconds")), config.getInt("tracking.min-blocks"),
                config.getInt("tracking.max-extent")));
        getServer().getPluginManager().registerEvents(new BlockChangeListener(tracker), this);
        maskMonitor = new MaskMonitor(this, tracker, new MaskProjector(), client, reporter);
        maskMonitor.start(20L * config.getLong("tracking.poll-seconds"));

        scanner = new WorldScanner(this, client, reporter, config.getInt("scan.chunks-per-tick"));
        if (config.getBoolean("scan.enabled")) {
            schedule = new DailySchedule(this, scanner, LocalTime.parse(config.getString("scan.daily-at")),
                    config.getStringList("scan.worlds"), getDataFolder().toPath().resolve("last-scan.txt"));
            schedule.start();
        }

        VistructumCommand command = new VistructumCommand(this, client, tracker, maskMonitor, scanner, reporter);
        Objects.requireNonNull(getCommand("vistructum")).setExecutor(command);
        Objects.requireNonNull(getCommand("vistructum")).setTabCompleter(command);
    }

    @Override
    public void onDisable() {
        if (schedule != null) {
            schedule.stop();
        }
        if (scanner != null) {
            scanner.close();
        }
        if (maskMonitor != null) {
            maskMonitor.stop();
        }
        if (reporter != null) {
            reporter.close();
        }
        if (client != null) {
            client.close();
        }
    }

    /** the sidecar serves whatever models sit in its model directory; say loudly when they do not fit this plugin */
    private void checkModels() {
        Map<String, String> expected = Map.of("mask", ModelContract.MASK_MODEL_VERSION,
                "fullscan", ModelContract.SCAN_MODEL_VERSION);
        client.version().whenComplete((versions, error) -> {
            if (error != null) {
                getLogger().warning("sidecar not reachable at startup: " + error.getMessage());
                return;
            }
            expected.forEach((kind, version) -> {
                if (!versions.has(kind)) {
                    getLogger().warning("sidecar has no " + kind + " model loaded");
                } else if (!version.equals(versions.getAsJsonObject(kind).get("model_version").getAsString())) {
                    getLogger().severe("sidecar " + kind + " model is " + versions.getAsJsonObject(kind)
                            .get("model_version").getAsString() + ", this plugin expects " + version);
                }
            });
        });
    }
}
