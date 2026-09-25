package de.kylekreuter.vistructum.ui;

import de.kylekreuter.vistructum.api.Vistructum;
import de.kylekreuter.vistructum.ui.gui.MapCards;
import de.kylekreuter.vistructum.ui.gui.MenuListener;
import de.kylekreuter.vistructum.ui.gui.PackDelivery;
import de.kylekreuter.vistructum.ui.gui.PackServer;
import de.kylekreuter.vistructum.ui.gui.ResourcePack;
import de.kylekreuter.vistructum.ui.gui.ReviewMenus;
import de.kylekreuter.vistructum.ui.text.Messages;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;

public final class VistructumUi extends JavaPlugin {

    private static final String STAFF_PERMISSION = "vistructum.staff";
    private static final String ADMIN_PERMISSION = "vistructum.admin";
    private static final String MESSAGES_FILE = "messages.yml";
    private static final String MAPS_FILE = "maps.yml";

    private PackServer packServer;
    private ScanBossBar scanBossBar;

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
        saveDefaultConfig();
        Clock clock = Clock.systemDefaultZone();
        Messages messages = Messages.from(loadMessages(), clock.getZone());
        ResourcePack pack = ResourcePack.build();
        int port = getConfig().getInt("pack.bind-port");
        try {
            packServer = new PackServer(port, pack);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "the resource pack cannot be served on port " + port, e);
        }
        ReviewMenus menus = new ReviewMenus(this, vistructum, messages, clock, loadMapCards());
        VisCommand command = new VisCommand(vistructum, menus, messages, STAFF_PERMISSION, ADMIN_PERMISSION, clock);
        PluginCommand vis = Objects.requireNonNull(getCommand("vis"));
        vis.setExecutor(command);
        vis.setTabCompleter(command);
        getServer().getPluginManager().registerEvents(new FindingAlerts(STAFF_PERMISSION, messages, clock), this);
        scanBossBar = new ScanBossBar(this, STAFF_PERMISSION, messages);
        getServer().getPluginManager().registerEvents(scanBossBar, this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        getServer().getPluginManager().registerEvents(new PackDelivery(pack,
                URI.create(Objects.requireNonNull(getConfig().getString("pack.public-url"), "pack.public-url")),
                STAFF_PERMISSION, messages), this);
    }

    @Override
    public void onDisable() {
        if (scanBossBar != null) {
            scanBossBar.close();
            scanBossBar = null;
        }
        if (packServer != null) {
            packServer.close();
            packServer = null;
        }
    }

    private Optional<MapCards> loadMapCards() {
        if (!getConfig().getBoolean("list.map-previews")) {
            return Optional.empty();
        }
        try {
            return Optional.of(MapCards.load(new File(getDataFolder(), MAPS_FILE), getServer().getWorlds().getFirst()));
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "the map ids cannot be stored in " + MAPS_FILE + ", map previews are off", e);
            return Optional.empty();
        }
    }

    private YamlConfiguration loadMessages() {
        File file = new File(getDataFolder(), MESSAGES_FILE);
        if (!file.exists()) {
            saveResource(MESSAGES_FILE, false);
        }
        YamlConfiguration messages = YamlConfiguration.loadConfiguration(file);
        try (Reader defaults = new InputStreamReader(Objects.requireNonNull(getResource(MESSAGES_FILE)),
                StandardCharsets.UTF_8)) {
            messages.setDefaults(YamlConfiguration.loadConfiguration(defaults));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return messages;
    }
}
