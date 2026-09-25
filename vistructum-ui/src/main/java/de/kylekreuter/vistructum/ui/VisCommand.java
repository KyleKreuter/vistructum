package de.kylekreuter.vistructum.ui;

import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.FindingQuery;
import de.kylekreuter.vistructum.api.ReviewState;
import de.kylekreuter.vistructum.api.InferenceStatus;
import de.kylekreuter.vistructum.api.Verdict;
import de.kylekreuter.vistructum.api.Vistructum;
import de.kylekreuter.vistructum.api.VistructumStatus;
import de.kylekreuter.vistructum.ui.gui.ListPosition;
import de.kylekreuter.vistructum.ui.gui.ReviewMenus;
import de.kylekreuter.vistructum.ui.text.Message;
import de.kylekreuter.vistructum.ui.text.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static de.kylekreuter.vistructum.ui.Replies.when;
import static de.kylekreuter.vistructum.ui.text.Messages.number;
import static de.kylekreuter.vistructum.ui.text.Messages.text;

public final class VisCommand implements TabExecutor {

    private static final int DEFAULT_REVIEW_LIMIT = 10;
    private static final int MAX_REVIEW_LIMIT = 50;
    private static final List<String> STAFF_SUBCOMMANDS = List.of("status", "review", "show", "tp", "confirm", "falsealarm");

    private final Vistructum vistructum;
    private final ReviewMenus menus;
    private final Messages messages;
    private final String staffPermission;
    private final String adminPermission;
    private final Clock clock;

    public VisCommand(Vistructum vistructum, ReviewMenus menus, Messages messages, String staffPermission,
                      String adminPermission, Clock clock) {
        this.vistructum = Objects.requireNonNull(vistructum, "vistructum");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.staffPermission = Objects.requireNonNull(staffPermission, "staffPermission");
        this.adminPermission = Objects.requireNonNull(adminPermission, "adminPermission");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "review" : args[0].toLowerCase();
        if (!sender.hasPermission(sub.equals("scan") ? adminPermission : staffPermission)) {
            reply(sender, Message.COMMAND_NO_PERMISSION);
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
            default -> reply(sender, Message.COMMAND_USAGE);
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
        when(sender, messages, vistructum.status(), status -> statusLines(status).forEach(sender::sendMessage));
    }

    private List<Component> statusLines(VistructumStatus status) {
        List<Component> lines = new ArrayList<>();
        InferenceStatus inference = status.inference();
        String mode = inference.mode().name().toLowerCase(Locale.ROOT);
        lines.add(inference.available()
                ? messages.chat(Message.STATUS_INFERENCE, text("mode", mode),
                text("models", inference.models().toString()))
                : messages.chat(Message.STATUS_INFERENCE_UNAVAILABLE, text("mode", mode),
                text("reason", inference.error().orElse("-"))));
        lines.add(messages.chat(Message.STATUS_CHANGES, number("count", status.trackedChanges())));
        lines.add(messages.chat(Message.STATUS_OPEN, number("count", status.openFindings())));
        if (status.activeScans().isEmpty()) {
            lines.add(messages.chat(Message.STATUS_NO_SCAN));
        }
        status.activeScans().forEach(job -> lines.add(ScanText.progress(messages, job)));
        return lines;
    }

    private void review(CommandSender sender, String[] args) {
        if (sender instanceof Player player) {
            menus.openList(player, ListPosition.first(ReviewState.OPEN));
            return;
        }
        int limit = DEFAULT_REVIEW_LIMIT;
        if (args.length > 1) {
            OptionalLong parsed = parse(args[1]);
            if (parsed.isEmpty() || parsed.getAsLong() < 1) {
                reply(sender, Message.COMMAND_COUNT_INVALID);
                return;
            }
            limit = (int) Math.min(MAX_REVIEW_LIMIT, parsed.getAsLong());
        }
        when(sender, messages, vistructum.findings().find(FindingQuery.open().limit(limit)), page -> {
            if (page.items().isEmpty()) {
                reply(sender, Message.REVIEW_EMPTY);
                return;
            }
            Instant now = clock.instant();
            reply(sender, Message.REVIEW_HEADER, number("count", page.items().size()));
            page.items().forEach(finding -> sender.sendMessage(
                    messages.chat(Message.REVIEW_LINE, messages.finding(finding, now))));
            if (page.hasNext()) {
                reply(sender, Message.REVIEW_MORE);
            }
        });
    }

    private void show(CommandSender sender, long id) {
        if (sender instanceof Player player) {
            menus.openDetail(player, id, ListPosition.first(ReviewState.OPEN));
            return;
        }
        withFinding(sender, id, finding -> {
            sender.sendMessage(messages.chat(Message.FINDING_DETAIL, messages.finding(finding, clock.instant()),
                    text("builder", builders(finding))));
            sender.sendMessage(finding.review()
                    .map(review -> FindingAlerts.verdict(messages, finding, review))
                    .orElseGet(() -> messages.chat(Message.FINDING_UNREVIEWED, number("id", id))));
        });
    }

    private void teleport(CommandSender sender, long id) {
        if (!(sender instanceof Player player)) {
            reply(sender, Message.COMMAND_PLAYER_ONLY);
            return;
        }
        withFinding(sender, id, finding -> Teleports.toFinding(player, finding, messages));
    }

    private void judge(CommandSender sender, long id, Verdict verdict) {
        when(sender, messages, vistructum.findings().review(id, verdict, sender.getName()), found -> found.ifPresentOrElse(
                finding -> {
                    if (!(sender instanceof Player)) {
                        finding.review().ifPresent(review -> sender.sendMessage(FindingAlerts.verdict(messages, finding, review)));
                    }
                },
                () -> reply(sender, Message.COMMAND_FINDING_MISSING, number("id", id))));
    }

    private void scan(CommandSender sender, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("stop")) {
            when(sender, messages, vistructum.scans().cancelAll(),
                    cancelled -> reply(sender, Message.SCAN_CANCELLED, number("count", cancelled.size())));
            return;
        }
        World world = args.length > 1 ? Bukkit.getWorld(args[1])
                : sender instanceof Player player ? player.getWorld() : Bukkit.getWorlds().getFirst();
        if (world == null) {
            reply(sender, Message.COMMAND_WORLD_UNKNOWN, text("world", args[1]));
            return;
        }
        when(sender, messages, vistructum.scans().request(world.getName()), job -> sender.sendMessage(job
                .map(queued -> messages.chat(Message.SCAN_QUEUED, ScanText.values(queued)))
                .orElseGet(() -> messages.chat(Message.SCAN_ALREADY_RUNNING, text("world", world.getName())))));
    }

    private void withFinding(CommandSender sender, long id, Consumer<Finding> action) {
        when(sender, messages, vistructum.findings().get(id), found -> found.ifPresentOrElse(action,
                () -> reply(sender, Message.COMMAND_FINDING_MISSING, number("id", id))));
    }

    private String builders(Finding finding) {
        if (finding.players().isEmpty()) {
            return messages.plain(Message.MENU_BUILDER_UNKNOWN);
        }
        return finding.players().stream().map(Bukkit::getOfflinePlayer).map(OfflinePlayer::getName)
                .map(name -> Objects.requireNonNullElseGet(name, () -> messages.plain(Message.MENU_BUILDER_UNKNOWN)))
                .sorted().collect(Collectors.joining(", "));
    }

    private void withId(CommandSender sender, String[] args, Consumer<Long> action) {
        OptionalLong id = args.length > 1 ? parse(args[1].replace("#", "")) : OptionalLong.empty();
        if (id.isEmpty()) {
            reply(sender, Message.COMMAND_ID_MISSING, text("subcommand", args[0]));
            return;
        }
        action.accept(id.getAsLong());
    }

    private void reply(CommandSender sender, Message message, TagResolver... resolvers) {
        sender.sendMessage(messages.chat(message, resolvers));
    }

    private static OptionalLong parse(String text) {
        try {
            return OptionalLong.of(Long.parseLong(text));
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }
}
