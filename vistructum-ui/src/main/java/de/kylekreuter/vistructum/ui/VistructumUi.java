package de.kylekreuter.vistructum.ui;

import de.kylekreuter.vistructum.api.Vistructum;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Clock;
import java.util.Objects;

public final class VistructumUi extends JavaPlugin {

    private static final String STAFF_PERMISSION = "vistructum.staff";
    private static final String ADMIN_PERMISSION = "vistructum.admin";

    @Override
    public void onEnable() {
        Vistructum vistructum;
        try {
            vistructum = Vistructum.get();
        } catch (IllegalStateException e) {
            getLogger().severe(e.getMessage() + ", disabling");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        VisCommand command = new VisCommand(vistructum, STAFF_PERMISSION, ADMIN_PERMISSION, Clock.systemDefaultZone());
        PluginCommand vis = Objects.requireNonNull(getCommand("vis"));
        vis.setExecutor(command);
        vis.setTabCompleter(command);
        getServer().getPluginManager().registerEvents(new FindingAlerts(STAFF_PERMISSION), this);
    }
}
