package de.kylekreuter.vistructum.core.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginVersionTest {

    @Test
    void comparesNumericParts() {
        assertTrue(PluginVersion.isNewer("1.1.0", "1.0.9"));
        assertTrue(PluginVersion.isNewer("1.10.0", "1.9.0"));
        assertTrue(PluginVersion.isNewer("2.0", "1.9.9"));
        assertFalse(PluginVersion.isNewer("1.0.0", "1.0.0"));
        assertFalse(PluginVersion.isNewer("1.0.0", "1.1.0"));
        assertFalse(PluginVersion.isNewer("1.0", "1.0.0"));
    }

    @Test
    void aReleaseIsNewerThanItsPreRelease() {
        assertTrue(PluginVersion.isNewer("1.1.0", "1.1.0-SNAPSHOT"));
        assertFalse(PluginVersion.isNewer("1.0.0", "1.1.0-SNAPSHOT"));
        assertFalse(PluginVersion.isNewer("1.1.0-rc.1", "1.1.0"));
    }

    @Test
    void ignoresUnreadableVersions() {
        assertFalse(PluginVersion.isNewer("latest", "1.0.0"));
        assertFalse(PluginVersion.isNewer("1.1.0", "dev"));
    }
}
