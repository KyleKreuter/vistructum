package de.kylekreuter.vistructum.core.metrics;

import de.kylekreuter.vistructum.api.InferenceMode;
import de.kylekreuter.vistructum.core.inference.InferenceSettings;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UsageMetricsTest {

    @Test
    void inferenceModeNamesLocal() {
        assertEquals("local", UsageMetrics.inferenceMode(settings(InferenceMode.LOCAL, true)));
    }

    @Test
    void inferenceModeNamesRemoteWithoutFallback() {
        assertEquals("remote", UsageMetrics.inferenceMode(settings(InferenceMode.REMOTE, false)));
    }

    @Test
    void inferenceModeNamesRemoteWithFallback() {
        assertEquals("remote + local fallback", UsageMetrics.inferenceMode(settings(InferenceMode.REMOTE, true)));
    }

    @Test
    void onOffPicksLabel() {
        assertEquals("on", UsageMetrics.onOff(true, "on", "off"));
        assertEquals("off", UsageMetrics.onOff(false, "on", "off"));
    }

    private static InferenceSettings settings(InferenceMode mode, boolean localFallback) {
        return new InferenceSettings(mode, localFallback, 1, true, "KyleKreuter/vistructum", Duration.ofHours(24),
                "127.0.0.1", 8000, Duration.ofSeconds(3), Duration.ofSeconds(120));
    }
}
