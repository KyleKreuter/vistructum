package de.kylekreuter.vistructum.core;

import de.kylekreuter.vistructum.core.alert.DetectionReporter;
import de.kylekreuter.vistructum.core.mask.MaskMonitor;
import de.kylekreuter.vistructum.core.scan.WorldScanner;
import de.kylekreuter.vistructum.core.sidecar.SidecarClient;
import de.kylekreuter.vistructum.core.tracking.ModificationTracker;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;

/** /vistructum status | scan [world] | scan stop */
final class VistructumCommand implements TabExecutor {

    private final Plugin plugin;
    private final SidecarClient client;
    private final ModificationTracker tracker;
    private final MaskMonitor maskMonitor;
    private final WorldScanner scanner;
    private final DetectionReporter reporter;

    VistructumCommand(Plugin plugin, SidecarClient client, ModificationTracker tracker, MaskMonitor maskMonitor,
                      WorldScanner scanner, DetectionReporter reporter) {
        this.plugin = plugin;
        this.client = client;
        this.tracker = tracker;
        this.maskMonitor = maskMonitor;
        this.scanner = scanner;
        this.reporter = reporter;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase();
        switch (sub) {
            case "status" -> status(sender);
            case "scan" -> scan(sender, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void status(CommandSender sender) {
        sender.sendMessage("Vistructum: " + tracker.trackedPositionCount() + " verfolgte Blockänderungen, "
                + maskMonitor.checkedProjections() + " Masken-Prüfungen, " + reporter.recentCount() + " aktuelle Funde");
        sender.sendMessage(scanner.status());
        client.health().whenComplete((health, error) -> Bukkit.getScheduler().runTask(plugin, () ->
                sender.sendMessage(error != null ? "Sidecar nicht erreichbar: " + error.getMessage()
                        : "Sidecar " + health.status() + ", Modelle " + health.models())));
    }

    private void scan(CommandSender sender, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("stop")) {
            scanner.stop();
            sender.sendMessage("Scan abgebrochen.");
            return;
        }
        World world = args.length > 1 ? Bukkit.getWorld(args[1])
                : sender instanceof Player player ? player.getWorld() : Bukkit.getWorlds().getFirst();
        if (world == null) {
            sender.sendMessage("Unbekannte Welt " + args[1]);
            return;
        }
        scanner.enqueue(world);
        sender.sendMessage("Scan von " + world.getName() + " eingereiht. Fortschritt: /vistructum status");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return List.of("status", "scan");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("scan")) {
            List<String> options = new java.util.ArrayList<>(Bukkit.getWorlds().stream().map(World::getName).toList());
            options.add("stop");
            return options;
        }
        return List.of();
    }
}
