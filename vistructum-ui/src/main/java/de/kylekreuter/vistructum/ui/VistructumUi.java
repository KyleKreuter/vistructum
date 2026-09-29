package de.kylekreuter.vistructum.ui;

import de.kylekreuter.vistructum.api.Vistructum;
import de.kylekreuter.vistructum.ui.gui.MapCards;
import de.kylekreuter.vistructum.ui.gui.MenuListener;
import de.kylekreuter.vistructum.ui.gui.PackDelivery;
import de.kylekreuter.vistructum.ui.gui.ResourcePack;
import de.kylekreuter.vistructum.ui.gui.ReviewMenus;
import de.kylekreuter.vistructum.ui.gui.Thumbnails;
import de.kylekreuter.vistructum.ui.text.Messages;
import de.kylekreuter.vistructum.ui.web.BukkitGameServer;
import de.kylekreuter.vistructum.ui.web.ReviewLinks;
import de.kylekreuter.vistructum.ui.web.WebApplication;
import de.kylekreuter.vistructum.ui.web.assets.GameAssets;
import de.kylekreuter.vistructum.ui.web.WebSettings;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;

public final class VistructumUi extends JavaPlugin {

    private static final String STAFF_PERMISSION = "vistructum.staff";
    private static final String ADMIN_PERMISSION = "vistructum.admin";
    private static final String SHARE_PERMISSION = "vistructum.evidence.share";
    private static final String MESSAGES_FILE = "messages.yml";
    private static final String MAPS_FILE = "maps.yml";
    private static final String ASSETS_FOLDER = "assets";

    private WebApplication webApplication;
    private GameAssets gameAssets;
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
        WebSettings web = WebSettings.from(getConfig());
        startWebApplication(vistructum, pack, web, clock);
        ReviewLinks links = new ReviewLinks(vistructum.web(), web, messages);
        Thumbnails thumbnails = new Thumbnails(this, vistructum);
        ReviewMenus menus = new ReviewMenus(this, vistructum, messages, clock, loadMapCards(), thumbnails, links);
        VisCommand command = new VisCommand(vistructum, menus, messages, STAFF_PERMISSION, ADMIN_PERMISSION,
                SHARE_PERMISSION, links, clock);
        PluginCommand vis = Objects.requireNonNull(getCommand("vis"));
        vis.setExecutor(command);
        vis.setTabCompleter(command);
        getServer().getPluginManager().registerEvents(new FindingAlerts(STAFF_PERMISSION, messages, clock), this);
        scanBossBar = new ScanBossBar(this, STAFF_PERMISSION, messages);
        getServer().getPluginManager().registerEvents(scanBossBar, this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        getServer().getPluginManager().registerEvents(thumbnails, this);
        getServer().getScheduler().runTask(this, thumbnails::fillMissing);
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
        if (webApplication != null) {
            webApplication.close();
            webApplication = null;
        }
        if (gameAssets != null) {
            gameAssets.close();
            gameAssets = null;
        }
    }

    private void startWebApplication(Vistructum vistructum, ResourcePack pack, WebSettings web, Clock clock) {
        int port = getConfig().getInt("pack.bind-port");
        InetSocketAddress address = web.enabled() ? new InetSocketAddress(web.bindAddress(), port)
                : new InetSocketAddress(port);
        gameAssets = new GameAssets(getDataFolder().toPath().resolve(ASSETS_FOLDER), Bukkit.getMinecraftVersion(),
                web.enabled() && getConfig().getBoolean("web.textures.download"));
        Optional<WebApplication.WebApp> app = web.enabled()
                ? Optional.of(new WebApplication.WebApp(vistructum, web, new BukkitGameServer(SHARE_PERMISSION),
                BukkitGameServer.palette(), gameAssets, Bukkit.getScheduler().getMainThreadExecutor(this),
                getClassLoader(), clock))
                : Optional.empty();
        try {
            webApplication = WebApplication.start(address, pack, app, getLogger());
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "the resource pack and the web app cannot be served on " + address, e);
            return;
        }
        if (web.enabled()) {
            getLogger().info("the web app is served on " + address + " and linked as " + web.publicUrl()
                    + WebSettings.HOME);
            gameAssets.fetch(GameAssets.MANIFEST, getLogger());
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
