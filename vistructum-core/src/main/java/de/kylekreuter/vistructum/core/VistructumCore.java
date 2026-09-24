package de.kylekreuter.vistructum.core;

import de.kylekreuter.vistructum.api.ModelContract;
import de.kylekreuter.vistructum.api.Vistructum;
import de.kylekreuter.vistructum.core.alert.FindingReporter;
import de.kylekreuter.vistructum.core.alert.FindingStore;
import de.kylekreuter.vistructum.core.mask.MaskMonitor;
import de.kylekreuter.vistructum.core.scan.DailySchedule;
import de.kylekreuter.vistructum.core.scan.ScanStore;
import de.kylekreuter.vistructum.core.scan.WorldScanner;
import de.kylekreuter.vistructum.core.scene.MaskProjector;
import de.kylekreuter.vistructum.core.sidecar.SidecarClient;
import de.kylekreuter.vistructum.core.store.Database;
import de.kylekreuter.vistructum.core.tracking.BlockChangeListener;
import de.kylekreuter.vistructum.core.tracking.BlockChangeStore;
import de.kylekreuter.vistructum.core.tracking.ClusterSettings;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

public final class VistructumCore extends JavaPlugin {

    private Database database;
    private SidecarClient client;
    private MaskMonitor maskMonitor;
    private WorldScanner scanner;
    private DailySchedule schedule;

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

        String host = Objects.requireNonNullElse(System.getenv("SIDECAR_HOST"), config.getString("sidecar.host"));
        int port = System.getenv("SIDECAR_PORT") != null ? Integer.parseInt(System.getenv("SIDECAR_PORT"))
                : config.getInt("sidecar.port");
        client = new SidecarClient(host, port, Duration.ofSeconds(config.getLong("sidecar.connect-timeout-seconds")),
                Duration.ofSeconds(config.getLong("sidecar.request-timeout-seconds")));
        checkModels();

        FindingReporter reporter = new FindingReporter(mainThread, findings, getLogger(),
                Duration.ofDays(config.getLong("alerts.dedupe-days")), clock);

        getServer().getPluginManager().registerEvents(new BlockChangeListener(changes, clock::millis), this);
        ClusterSettings clusterSettings = new ClusterSettings(Duration.ofMinutes(config.getLong("tracking.ttl-minutes")),
                config.getInt("tracking.link-distance"), Duration.ofSeconds(config.getLong("tracking.quiet-seconds")),
                config.getInt("tracking.min-blocks"), config.getInt("tracking.max-extent"));
        maskMonitor = new MaskMonitor(mainThread, changes, clusterSettings, new MaskProjector(), client, reporter, clock);
        maskMonitor.start(20L * config.getLong("tracking.poll-seconds"));

        scanner = new WorldScanner(mainThread, scans, client, reporter, config.getInt("scan.chunks-per-tick"), clock);
        scanner.start();
        if (config.getBoolean("scan.enabled")) {
            schedule = new DailySchedule(mainThread, scans, scanner, LocalTime.parse(config.getString("scan.daily-at")),
                    config.getStringList("scan.worlds"), clock);
            schedule.start();
        }

        getServer().getServicesManager().register(Vistructum.class,
                new VistructumService(mainThread, changes, findings, scans, scanner, client, clock), this,
                ServicePriority.Normal);
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (schedule != null) {
            schedule.stop();
        }
        if (scanner != null) {
            scanner.close();
        }
        if (maskMonitor != null) {
            maskMonitor.stop();
        }
        if (client != null) {
            client.close();
        }
        if (database != null) {
            database.close();
        }
    }

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
                    return;
                }
                String loaded = versions.getAsJsonObject(kind).get("model_version").getAsString();
                if (!version.equals(loaded)) {
                    getLogger().severe("sidecar " + kind + " model is " + loaded + ", this plugin expects " + version);
                }
            });
        });
    }
}
