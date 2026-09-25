package de.kylekreuter.vistructum.core.inference;

import de.kylekreuter.vistructum.api.InferenceMode;
import org.bukkit.configuration.ConfigurationSection;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public record InferenceSettings(InferenceMode mode, boolean localFallback, int threads, boolean autoUpdate,
                                String repository, Duration checkInterval, String sidecarHost, int sidecarPort,
                                Duration connectTimeout, Duration requestTimeout) {

    public static InferenceSettings from(ConfigurationSection config, Map<String, String> env) {
        InferenceMode mode = switch (lower(config.getString("inference.mode"))) {
            case "local" -> InferenceMode.LOCAL;
            case "remote" -> InferenceMode.REMOTE;
            default -> throw new IllegalArgumentException("inference.mode must be local or remote");
        };
        boolean localFallback = switch (lower(config.getString("inference.fallback"))) {
            case "none" -> false;
            case "local" -> true;
            default -> throw new IllegalArgumentException("inference.fallback must be none or local");
        };
        boolean autoUpdate = switch (lower(config.getString("models.update"))) {
            case "auto" -> true;
            case "off" -> false;
            default -> throw new IllegalArgumentException("models.update must be auto or off");
        };
        int threads = config.getInt("inference.threads");
        long checkHours = config.getLong("models.check-hours");
        if (threads < 1 || checkHours < 1) {
            throw new IllegalArgumentException("inference.threads and models.check-hours must be at least 1");
        }
        String host = Objects.requireNonNullElse(env.get("SIDECAR_HOST"), config.getString("sidecar.host"));
        int port = env.get("SIDECAR_PORT") != null ? Integer.parseInt(env.get("SIDECAR_PORT"))
                : config.getInt("sidecar.port");
        return new InferenceSettings(mode, localFallback, threads, autoUpdate,
                Objects.requireNonNull(config.getString("models.repository"), "models.repository"),
                Duration.ofHours(checkHours), host, port,
                Duration.ofSeconds(config.getLong("sidecar.connect-timeout-seconds")),
                Duration.ofSeconds(config.getLong("sidecar.request-timeout-seconds")));
    }

    public boolean runsLocalModels() {
        return mode == InferenceMode.LOCAL || localFallback;
    }

    private static String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
