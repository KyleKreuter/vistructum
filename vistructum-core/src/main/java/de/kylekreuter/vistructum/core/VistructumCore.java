package de.kylekreuter.vistructum.core;

import org.bukkit.plugin.java.JavaPlugin;

public final class VistructumCore extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
    }

    @Override
    public void onDisable() {
    }
}
