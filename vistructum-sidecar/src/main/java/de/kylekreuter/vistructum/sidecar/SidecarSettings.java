package de.kylekreuter.vistructum.sidecar;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

public record SidecarSettings(Path modelDirectory, int port, int threads, boolean autoUpdate, String repository,
                              Duration checkInterval) {

    public static SidecarSettings fromEnvironment(Map<String, String> env) {
        String update = env.getOrDefault("MODELS_UPDATE", "auto");
        if (!update.equals("auto") && !update.equals("off")) {
            throw new IllegalArgumentException("MODELS_UPDATE must be auto or off, got " + update);
        }
        return new SidecarSettings(
                Path.of(env.getOrDefault("MODEL_DIR", "/models")),
                Integer.parseInt(env.getOrDefault("PORT", "8000")),
                Integer.parseInt(env.getOrDefault("ORT_THREADS", "1")),
                update.equals("auto"),
                env.getOrDefault("MODELS_REPOSITORY", "KyleKreuter/vistructum"),
                Duration.ofHours(Long.parseLong(env.getOrDefault("MODELS_CHECK_HOURS", "24"))));
    }
}
