package de.kylekreuter.vistructum.ui;

import de.kylekreuter.vistructum.api.SidecarStatus;
import de.kylekreuter.vistructum.api.Verdict;
import de.kylekreuter.vistructum.api.VistructumApi;
import de.kylekreuter.vistructum.api.VistructumStatus;
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
import java.util.function.Consumer;

import static de.kylekreuter.vistructum.ui.Replies.error;
import static de.kylekreuter.vistructum.ui.Replies.info;

public final class VisCommand implements TabExecutor {

    private static final int DEFAULT_REVIEW_LIMIT = 10;
    private static final int MAX_REVIEW_LIMIT = 50;
    private static final List<String> STAFF_SUBCOMMANDS = List.of("status", "review", "show", "tp", "confirm", "falsealarm");

    private final VistructumApi api;
    private final Replies replies;
    private final String staffPermission;
    private final String adminPermission;
    private final Clock clock;

    public VisCommand(VistructumApi api, Replies replies, String staffPermission, String adminPermission, Clock clock) {
        this.api = Objects.requireNonNull(api, "api");
        this.replies = Objects.requireNonNull(replies, "replies");
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
        replies.when(sender, api.status(), status -> statusLines(status).forEach(line -> info(sender, line)));
    }

    private static List<String> statusLines(VistructumStatus status) {
        List<String> lines = new ArrayList<>();
        SidecarStatus sidecar = status.sidecar();
        lines.add(sidecar.reachable()
                ? "Sidecar " + sidecar.status() + ", Modelle " + sidecar.models()
                : "Sidecar nicht erreichbar: " + sidecar.error().orElse("unbekannter Fehler"));
        lines.add(status.trackedChanges() + " Blockänderungen im Beobachtungsfenster");
        lines.add(status.openFindings() + " offene Funde, Liste: /vis review");
        if (status.activeScans().isEmpty()) {
            lines.add("Kein Scan aktiv");
        }
        status.activeScans().forEach(job -> lines.add(ChatViews.describe(job)));
        return lines;
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
        replies.when(sender, api.openFindings(limit), open -> {
            if (open.isEmpty()) {
                info(sender, "Keine offenen Funde.");
                return;
            }
            info(sender, open.size() + " offene Funde, neueste zuerst:");
            open.forEach(finding -> sender.sendMessage(ChatViews.reviewLine(finding, clock.instant())));
        });
    }

    private void show(CommandSender sender, long id) {
        replies.when(sender, api.finding(id).thenCombine(api.preview(id), (finding, preview) ->
                finding.flatMap(f -> preview.map(p -> ChatViews.details(f, p)))), details -> details.ifPresentOrElse(
                lines -> lines.forEach(sender::sendMessage), () -> error(sender, "Fund #" + id + " gibt es nicht.")));
    }

    private void teleport(CommandSender sender, long id) {
        if (!(sender instanceof Player player)) {
            error(sender, "Nur Spieler können teleportieren.");
            return;
        }
        replies.when(sender, api.finding(id), found -> found.ifPresentOrElse(finding -> {
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
        replies.when(sender, api.review(id, verdict, sender.getName()), found -> found.ifPresentOrElse(
                finding -> info(sender, "Fund #" + id + ": " + finding.review().map(ChatViews::reviewText).orElseThrow()),
                () -> error(sender, "Fund #" + id + " gibt es nicht.")));
    }

    private void scan(CommandSender sender, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("stop")) {
            replies.when(sender, api.cancelScans(), count -> info(sender, count + " Scans abgebrochen."));
            return;
        }
        World world = args.length > 1 ? Bukkit.getWorld(args[1])
                : sender instanceof Player player ? player.getWorld() : Bukkit.getWorlds().getFirst();
        if (world == null) {
            error(sender, "Unbekannte Welt " + args[1]);
            return;
        }
        replies.when(sender, api.requestScan(world.getName()), job -> info(sender, job.isPresent()
                ? "Scan #" + job.get().id() + " von " + world.getName() + " eingereiht. Fortschritt: /vis status"
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

    private static OptionalLong parse(String text) {
        try {
            return OptionalLong.of(Long.parseLong(text));
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }
}
