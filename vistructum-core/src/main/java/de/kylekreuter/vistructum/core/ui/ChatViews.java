package de.kylekreuter.vistructum.core.ui;

import de.kylekreuter.vistructum.api.BlockBox;
import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.Review;
import de.kylekreuter.vistructum.api.Source;
import de.kylekreuter.vistructum.api.Verdict;
import de.kylekreuter.vistructum.core.alert.Preview;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ChatViews {

    public static final String COMMAND = "/vis";
    private static final int PREVIEW_MAX_SIDE = 32;
    private static final String PIXEL = "█";

    private ChatViews() {
    }

    public static String describe(Finding finding) {
        BlockBox box = finding.box();
        String players = playerNames(finding.players());
        return String.format(Locale.ROOT, "Fund #%d: Verdacht auf Hakenkreuz (%s, Score %.3f, %d Fenster, %s) in %s bei "
                        + "x=%d..%d y=%d..%d z=%d..%d%s", finding.id(), sourceName(finding.source()), finding.score(),
                finding.votes(), finding.detail(), finding.world(), box.minX(), box.maxX(), box.minY(), box.maxY(),
                box.minZ(), box.maxZ(), players.isEmpty() ? "" : ", Spieler: " + players);
    }

    public static Component alert(Finding finding) {
        return prefix()
                .append(Component.text(describe(finding), NamedTextColor.YELLOW))
                .append(Component.space())
                .append(button("Ansehen", "show", finding.id(), NamedTextColor.GRAY))
                .append(Component.space())
                .append(button("TP", "tp", finding.id(), NamedTextColor.AQUA));
    }

    public static Component reviewLine(Finding finding, Instant now) {
        BlockBox box = finding.box();
        String summary = String.format(Locale.ROOT, "#%d %s %s %d %d %d Score %.2f vor %s", finding.id(),
                sourceName(finding.source()), finding.world(), box.centerX(), box.maxY(), box.centerZ(), finding.score(),
                age(Duration.between(finding.createdAt(), now)));
        return Component.text(summary, NamedTextColor.YELLOW)
                .append(Component.space())
                .append(button("TP", "tp", finding.id(), NamedTextColor.AQUA))
                .append(Component.space())
                .append(button("Ansehen", "show", finding.id(), NamedTextColor.GRAY))
                .append(Component.space())
                .append(button("Bestätigen", "confirm", finding.id(), NamedTextColor.RED))
                .append(Component.space())
                .append(button("Fehlalarm", "falsealarm", finding.id(), NamedTextColor.GREEN));
    }

    public static List<Component> details(Finding finding, Preview preview) {
        List<Component> lines = new ArrayList<>();
        lines.add(prefix().append(Component.text(describe(finding), NamedTextColor.YELLOW)));
        lines.addAll(preview(preview));
        lines.add(Component.text(finding.review().map(ChatViews::reviewText).orElse("Noch nicht bewertet."),
                        NamedTextColor.GRAY)
                .append(Component.space())
                .append(button("TP", "tp", finding.id(), NamedTextColor.AQUA))
                .append(Component.space())
                .append(button("Bestätigen", "confirm", finding.id(), NamedTextColor.RED))
                .append(Component.space())
                .append(button("Fehlalarm", "falsealarm", finding.id(), NamedTextColor.GREEN)));
        return lines;
    }

    public static List<Component> preview(Preview preview) {
        int step = Math.max(1, Math.ceilDiv(Math.max(preview.width(), preview.height()), PREVIEW_MAX_SIDE));
        List<Component> rows = new ArrayList<>();
        for (int row = 0; row < preview.height(); row += step) {
            TextComponent.Builder line = Component.text();
            for (int col = 0; col < preview.width(); col += step) {
                int grey = darkest(preview, row, col, step);
                line.append(Component.text(PIXEL, TextColor.color(grey, grey, grey)));
            }
            rows.add(line.build());
        }
        return rows;
    }

    public static Component prefix() {
        return Component.text("[Vistructum] ", NamedTextColor.RED);
    }

    public static String reviewText(Review review) {
        return (review.verdict() == Verdict.CONFIRMED ? "Bestätigt" : "Fehlalarm") + " von " + review.reviewer();
    }

    private static int darkest(Preview preview, int row, int col, int step) {
        int darkest = 255;
        for (int r = row; r < Math.min(preview.height(), row + step); r++) {
            for (int c = col; c < Math.min(preview.width(), col + step); c++) {
                darkest = Math.min(darkest, preview.grey(r, c));
            }
        }
        return darkest;
    }

    private static Component button(String label, String subcommand, long id, NamedTextColor color) {
        String command = COMMAND + " " + subcommand + " " + id;
        return Component.text("[" + label + "]", color)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(command)));
    }

    private static String sourceName(Source source) {
        return switch (source) {
            case MASK -> "Bauaktivität";
            case FULLSCAN -> "Oberflächenscan";
        };
    }

    private static String age(Duration age) {
        if (age.toMinutes() < 60) {
            return Math.max(0, age.toMinutes()) + " min";
        }
        if (age.toHours() < 48) {
            return age.toHours() + " h";
        }
        return age.toDays() + " d";
    }

    private static String playerNames(Set<UUID> players) {
        return players.stream().map(Bukkit::getOfflinePlayer).map(OfflinePlayer::getName).filter(Objects::nonNull)
                .sorted().collect(Collectors.joining(", "));
    }
}
