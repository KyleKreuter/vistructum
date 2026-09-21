package de.kylekreuter.vistructum.core;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VistructumCoreTest {

    @Test
    void entryPointIsPlugin() {
        assertTrue(JavaPlugin.class.isAssignableFrom(VistructumCore.class));
    }
}
