package de.kylekreuter.vistructum.core.ui;

import de.kylekreuter.vistructum.api.Verdict;
import de.kylekreuter.vistructum.core.MainThread;
import de.kylekreuter.vistructum.core.alert.FindingStore;
import de.kylekreuter.vistructum.core.scan.ScanCause;
import de.kylekreuter.vistructum.core.scan.ScanJob;
import de.kylekreuter.vistructum.core.scan.ScanStore;
import de.kylekreuter.vistructum.core.scan.WorldScanner;
import de.kylekreuter.vistructum.core.sidecar.SidecarClient;
import de.kylekreuter.vistructum.core.tracking.BlockChangeStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class VisCommand implements TabExecutor {

    private static final int DEFAULT_REVIEW_LIMIT = 10;
    private static final int MAX_REVIEW_LIMIT = 50;
    private static final List<String> STAFF_SUBCOMMANDS = List.of("status", "review", "show", "tp", "confirm", "falsealarm");

    private final MainThread mainThread;
    private final BlockChangeStore changes;
    private final FindingStore findings;
    private final ScanStore scans;
    private final WorldScanner scanner;
    private final SidecarClient client;
    private final String staffPermission;
    private final String adminPermission;
    private final Clock clock;

    public VisCommand(MainThread mainThread, BlockChangeStore changes, FindingStore findings, ScanStore scans,
                      WorldScanner scanner, SidecarClient client, String staffPermission, String adminPermission,
                      Clock clock) {
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.changes = Objects.requireNonNull(changes, "changes");
        this.findings = Objects.requireNonNull(findings, "findings");
        this.scans = Objects.requireNonNull(scans, "scans");
        this.scanner = Objects.requireNonNull(scanner, "scanner");
        this.client = Objects.requireNonNull(client, "client");
        this.staffPermission = Objects.requireNonNull(staffPermission, "staffPermission");
        this.adminPermission = Objects.requireNonNull(adminPermission, "adminPermission");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase();
        if (!sender.hasPermission(sub.equals("scan") ? adminPermission : staffPermission)) {
            sender.sendMessage(Component.text("Dafür fehlt dir die Berechtigung.", NamedTextColor.RED));
            return true;
        }
        switch (sub) {
            case "status" -> status(sender);
            case "review" -> review(sender, args);
            case "show" -> withId(sender, args, id -> show(sender, id));
            case "tp" -> withId(sender, args, id -> teleport(sender, id));
            case "confirm" -> withId(sender, args, id -> judge(sender, id, Verdict.CONFIRMED));
            case "falsealarm" -> withId(sender, args, id -> judge(sender, id, Verdict.FALSE_ALARM));
            case "scan" -> scan(sender, args);
            default -> usage(sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (sender.hasPermission(staffPermission)) {
                options.addAll(STAFF_SUBCOMMANDS);
            }
            if (sender.hasPermission(adminPermission)) {
                options.add("scan");
            }
            return options.stream().filter(option -> option.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("scan") && sender.hasPermission(adminPermission)) {
            List<String> options = new ArrayList<>(Bukkit.getWorlds().stream().map(World::getName).toList());
            options.add("stop");
            return options.stream().filter(option -> option.startsWith(args[1])).toList();
        }
        return List.of();
    }

    private void status(CommandSender sender) {
        reply(sender, changes.count(), count -> info(sender, count + " Blockänderungen im Beobachtungsfenster"));
        reply(sender, findings.countOpen(), count -> info(sender, count + " offene Funde, Liste: /vis review"));
        reply(sender, scans.active(), jobs -> {
            if (jobs.isEmpty()) {
                info(sender, "Kein Scan aktiv");
            }
            jobs.forEach(job -> info(sender, describe(job)));
        });
        client.health().whenComplete((health, error) -> mainThread.run(() -> info(sender, error != null
                ? "Sidecar nicht erreichbar: " + error.getMessage()
                : "Sidecar " + health.status() + ", Modelle " + health.models())));
    }

    private void review(CommandSender sender, String[] args) {
        int limit = DEFAULT_REVIEW_LIMIT;
        if (args.length > 1) {
            OptionalLong parsed = parse(args[1]);
            if (parsed.isEmpty() || parsed.getAsLong() < 1) {
                error(sender, "Anzahl muss eine positive Zahl sein.");
                return;
            }
            limit = (int) Math.min(MAX_REVIEW_LIMIT, parsed.getAsLong());
        }
        reply(sender, findings.open(limit), open -> {
            if (open.isEmpty()) {
                info(sender, "Keine offenen Funde.");
                return;
            }
            info(sender, open.size() + " offene Funde, neueste zuerst:");
            open.forEach(finding -> sender.sendMessage(ChatViews.reviewLine(finding, clock.instant())));
        });
    }

    private void show(CommandSender sender, long id) {
        reply(sender, findings.find(id).thenCombine(findings.preview(id), (finding, preview) ->
                finding.flatMap(f -> preview.map(p -> ChatViews.details(f, p)))), details -> details.ifPresentOrElse(
                lines -> lines.forEach(sender::sendMessage), () -> error(sender, "Fund #" + id + " gibt es nicht.")));
    }

    private void teleport(CommandSender sender, long id) {
        if (!(sender instanceof Player player)) {
            error(sender, "Nur Spieler können teleportieren.");
            return;
        }
        reply(sender, findings.find(id), found -> found.ifPresentOrElse(finding -> {
            World world = Bukkit.getWorld(finding.world());
            if (world == null) {
                error(sender, "Welt " + finding.world() + " ist nicht geladen.");
                return;
            }
            player.teleportAsync(new Location(world, finding.box().centerX() + 0.5, finding.box().maxY() + 3,
                    finding.box().centerZ() + 0.5));
        }, () -> error(sender, "Fund #" + id + " gibt es nicht.")));
    }

    private void judge(CommandSender sender, long id, Verdict verdict) {
        reply(sender, findings.review(id, verdict, sender.getName(), clock.instant()), found -> found.ifPresentOrElse(
                finding -> info(sender, "Fund #" + id + ": " + finding.review().map(ChatViews::reviewText).orElseThrow()),
                () -> error(sender, "Fund #" + id + " gibt es nicht.")));
    }

    private void scan(CommandSender sender, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("stop")) {
            reply(sender, scanner.cancel(), count -> info(sender, count + " Scans abgebrochen."));
            return;
        }
        World world = args.length > 1 ? Bukkit.getWorld(args[1])
                : sender instanceof Player player ? player.getWorld() : Bukkit.getWorlds().getFirst();
        if (world == null) {
            error(sender, "Unbekannte Welt " + args[1]);
            return;
        }
        reply(sender, scanner.enqueue(world.getName(), ScanCause.MANUAL), job -> info(sender, job.isPresent()
                ? "Scan von " + world.getName() + " eingereiht. Fortschritt: /vis status"
                : "Für " + world.getName() + " läuft schon ein Scan."));
    }

    private void usage(CommandSender sender) {
        info(sender, "/vis status | review [anzahl] | show <id> | tp <id> | confirm <id> | falsealarm <id> | "
                + "scan [welt|stop]");
    }

    private void withId(CommandSender sender, String[] args, Consumer<Long> action) {
        OptionalLong id = args.length > 1 ? parse(args[1].replace("#", "")) : OptionalLong.empty();
        if (id.isEmpty()) {
            error(sender, "Bitte eine Fund-ID angeben, zum Beispiel /vis " + args[0] + " 12");
            return;
        }
        action.accept(id.getAsLong());
    }

    private <T> void reply(CommandSender sender, CompletableFuture<T> future, Consumer<T> then) {
        future.whenComplete((value, error) -> mainThread.run(() -> {
            if (error != null) {
                error(sender, "Fehler: " + error.getMessage());
            } else {
                then.accept(value);
            }
        }));
    }

    private static String describe(ScanJob job) {
        return job.tilesTotal() == 0
                ? "Scan #" + job.id() + " " + job.world() + ": plant Kacheln"
                : "Scan #" + job.id() + " " + job.world() + ": Kachel " + job.tilesDone() + "/" + job.tilesTotal() + ", "
                + job.findings() + " Funde, " + job.failures() + " Fehler";
    }

    private static OptionalLong parse(String text) {
        try {
            return OptionalLong.of(Long.parseLong(text));
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    private static void info(CommandSender sender, String text) {
        sender.sendMessage(ChatViews.prefix().append(Component.text(text, NamedTextColor.GRAY)));
    }

    private static void error(CommandSender sender, String text) {
        sender.sendMessage(ChatViews.prefix().append(Component.text(text, NamedTextColor.RED)));
    }
}
