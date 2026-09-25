package de.kylekreuter.vistructum.core.inference;

import de.kylekreuter.vistructum.api.InferenceMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InferenceSettingsTest {

    @Test
    void defaultConfigRunsLocallyWithDailyUpdates() throws Exception {
        InferenceSettings settings = InferenceSettings.from(defaults(), Map.of());

        assertEquals(InferenceMode.LOCAL, settings.mode());
        assertFalse(settings.localFallback());
        assertTrue(settings.runsLocalModels());
        assertTrue(settings.autoUpdate());
        assertEquals("KyleKreuter/vistructum", settings.repository());
        assertEquals(Duration.ofHours(24), settings.checkInterval());
    }

    @Test
    void remoteWithoutFallbackLoadsNoLocalModels() throws Exception {
        YamlConfiguration config = defaults();
        config.set("inference.mode", "remote");

        assertFalse(InferenceSettings.from(config, Map.of()).runsLocalModels());
    }

    @Test
    void remoteWithLocalFallbackLoadsLocalModels() throws Exception {
        YamlConfiguration config = defaults();
        config.set("inference.mode", "REMOTE");
        config.set("inference.fallback", "local");

        InferenceSettings settings = InferenceSettings.from(config, Map.of());

        assertEquals(InferenceMode.REMOTE, settings.mode());
        assertTrue(settings.runsLocalModels());
    }

    @Test
    void environmentOverridesTheSidecarAddress() throws Exception {
        InferenceSettings settings = InferenceSettings.from(defaults(),
                Map.of("SIDECAR_HOST", "sidecar", "SIDECAR_PORT", "9000"));

        assertEquals("sidecar", settings.sidecarHost());
        assertEquals(9000, settings.sidecarPort());
    }

    @Test
    void unknownValuesAreRejected() throws Exception {
        for (String[] invalid : new String[][] {{"inference.mode", "cloud"}, {"inference.fallback", "remote"},
                {"models.update", "weekly"}}) {
            YamlConfiguration config = defaults();
            config.set(invalid[0], invalid[1]);
            assertThrows(IllegalArgumentException.class, () -> InferenceSettings.from(config, Map.of()),
                    invalid[0]);
        }
    }

    @Test
    void nonPositiveIntervalsAreRejected() throws Exception {
        YamlConfiguration config = defaults();
        config.set("models.check-hours", 0);

        assertThrows(IllegalArgumentException.class, () -> InferenceSettings.from(config, Map.of()));
    }

    private static YamlConfiguration defaults() throws Exception {
        try (Reader reader = new InputStreamReader(Objects.requireNonNull(
                InferenceSettingsTest.class.getResourceAsStream("/config.yml")), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }
}
