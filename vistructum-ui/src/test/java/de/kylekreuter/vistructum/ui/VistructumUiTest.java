package de.kylekreuter.vistructum.ui;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VistructumUiTest {

    @Test
    void entryPointIsPlugin() {
        assertTrue(JavaPlugin.class.isAssignableFrom(VistructumUi.class));
    }
}
