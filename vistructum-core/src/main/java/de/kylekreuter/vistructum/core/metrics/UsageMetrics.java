package de.kylekreuter.vistructum.core.metrics;

import de.kylekreuter.vistructum.api.InferenceMode;
import de.kylekreuter.vistructum.core.inference.InferenceSettings;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.BooleanSupplier;

public final class UsageMetrics {

    private static final int PLUGIN_ID = 34300;
    private static final String UI_PLUGIN = "vistructum-ui";

    private UsageMetrics() {
    }

    public static Metrics start(JavaPlugin plugin, InferenceSettings settings, boolean dailyScan, int scanWorlds) {
        Metrics metrics = new Metrics(plugin, PLUGIN_ID);
        BooleanSupplier uiInstalled = () -> plugin.getServer().getPluginManager().isPluginEnabled(UI_PLUGIN);
        metrics.addCustomChart(new SimplePie("inference_mode", () -> inferenceMode(settings)));
        metrics.addCustomChart(new SimplePie("model_updates", () -> onOff(settings.autoUpdate(), "auto", "off")));
        metrics.addCustomChart(new SimplePie("daily_scan", () -> onOff(dailyScan, "on", "off")));
        metrics.addCustomChart(new SimplePie("scan_worlds", () -> String.valueOf(scanWorlds)));
        metrics.addCustomChart(new SimplePie("ui_installed", () -> onOff(uiInstalled.getAsBoolean(), "yes", "no")));
        return metrics;
    }

    static String inferenceMode(InferenceSettings settings) {
        if (settings.mode() == InferenceMode.LOCAL) {
            return "local";
        }
        return settings.localFallback() ? "remote + local fallback" : "remote";
    }

    static String onOff(boolean value, String on, String off) {
        return value ? on : off;
    }
}
