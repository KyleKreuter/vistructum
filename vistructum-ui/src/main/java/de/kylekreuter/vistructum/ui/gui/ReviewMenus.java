package de.kylekreuter.vistructum.ui.gui;

import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.FindingQuery;
import de.kylekreuter.vistructum.api.Page;
import de.kylekreuter.vistructum.api.Review;
import de.kylekreuter.vistructum.api.ReviewState;
import de.kylekreuter.vistructum.api.Verdict;
import de.kylekreuter.vistructum.api.Vistructum;
import de.kylekreuter.vistructum.ui.Teleports;
import de.kylekreuter.vistructum.ui.text.Message;
import de.kylekreuter.vistructum.ui.text.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import static de.kylekreuter.vistructum.ui.text.Messages.number;
import static de.kylekreuter.vistructum.ui.text.Messages.text;

public final class ReviewMenus {

    private final Plugin plugin;
    private final Vistructum vistructum;
    private final Messages messages;
    private final Clock clock;
    private final Executor mainThread;

    public ReviewMenus(Plugin plugin, Vistructum vistructum, Messages messages, Clock clock) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.vistructum = Objects.requireNonNull(vistructum, "vistructum");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.mainThread = Bukkit.getScheduler().getMainThreadExecutor(plugin);
    }

    public void openList(Player player, ListPosition position) {
        CompletableFuture<Page<Finding>> page = vistructum.findings().find(position.query(Layout.LIST_CARDS.size()));
        CompletableFuture<Long> total = vistructum.findings().count(FindingQuery.all().state(position.state()));
        deliver(player, page.thenCombine(total, ListView::new), view -> {
            if (view.page().items().isEmpty() && position.page() > 1) {
                openList(player, position.previous());
                return;
            }
            showList(player, position, view);
        });
    }

    public void openDetail(Player player, long id, ListPosition back) {
        deliver(player, vistructum.findings().get(id).thenCompose(found -> found
                .map(finding -> scene(finding).thenCombine(builder(finding), (scene, builder) ->
                        Optional.of(new DetailView(finding, scene, builder))))
                .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty()))), view -> view.ifPresentOrElse(
                detail -> showDetail(player, detail, back),
                () -> player.sendMessage(messages.chat(Message.COMMAND_FINDING_MISSING, number("id", id)))));
    }

    private void showList(Player player, ListPosition position, ListView view) {
        List<Finding> items = view.page().items();
        long pages = Math.max(1, Math.ceilDiv(view.total(), Layout.LIST_CARDS.size()));
        Menu menu = new Menu(54, PixelText.list(messages.plain(Message.MENU_PAGE,
                number("page", position.page()), number("pages", pages))));
        Instant now = clock.instant();
        for (int index = 0; index < items.size(); index++) {
            Finding finding = items.get(index);
            menu.put(Layout.LIST_CARDS.get(index), card(finding, now),
                    clicked -> openDetail(clicked, finding.id(), position));
        }
        if (position.page() > 1) {
            menu.put(Layout.PREVIOUS_PAGE, icon("previous", messages.item(Message.MENU_PREVIOUS), List.of()),
                    clicked -> openList(clicked, position.previous()));
        } else {
            menu.put(Layout.PREVIOUS_PAGE, disabled("previous_disabled"), clicked -> {
            });
        }
        if (view.page().hasNext()) {
            menu.put(Layout.NEXT_PAGE, icon("next", messages.item(Message.MENU_NEXT), List.of()),
                    clicked -> openList(clicked, position.next(items.getLast().id())));
        } else {
            menu.put(Layout.NEXT_PAGE, disabled("next_disabled"), clicked -> {
            });
        }
        boolean open = position.state() == ReviewState.OPEN;
        menu.put(Layout.FILTER, icon(open ? "filter_open" : "filter_closed",
                        messages.item(open ? Message.MENU_FILTER_OPEN : Message.MENU_FILTER_CLOSED),
                        List.of(messages.item(open ? Message.MENU_FILTER_OPEN_HINT : Message.MENU_FILTER_CLOSED_HINT))),
                clicked -> openList(clicked, position.toggled()));
        player.openInventory(menu.getInventory());
    }

    private ItemStack card(Finding finding, Instant now) {
        TagResolver values = messages.finding(finding, now);
        List<Component> lore = new ArrayList<>();
        lore.add(messages.item(Message.MENU_CARD_WORLD, values));
        lore.add(messages.item(Message.MENU_CARD_LOCATION, values));
        lore.add(messages.item(Message.MENU_CARD_PROBABILITY, values));
        lore.add(messages.item(Message.MENU_CARD_AGE, values));
        finding.review().ifPresent(review -> lore.add(messages.item(review.verdict() == Verdict.CONFIRMED
                ? Message.MENU_CARD_CONFIRMED : Message.MENU_CARD_FALSE_ALARM, text("reviewer", review.reviewer()))));
        lore.add(messages.item(Message.MENU_CARD_HINT));
        String model = finding.review().map(Review::verdict)
                .map(verdict -> verdict == Verdict.CONFIRMED ? "card_confirmed" : "card_dismissed")
                .orElse("card");
        return icon(model, messages.item(Message.MENU_CARD, values), lore);
    }

    private void showDetail(Player player, DetailView view, ListPosition back) {
        Finding finding = view.finding();
        String label = messages.plain(Message.MENU_FINDING, number("id", finding.id()));
        Component title = PixelText.detail(label, view.scene(), view.builder().face().orElseGet(Faces::placeholder),
                view.builder().name(), fields(finding));
        Menu menu = new Menu(9 * Layout.DETAIL_ROWS, title);
        menu.put(Layout.CONFIRM, icon("confirm", messages.item(Message.MENU_CONFIRM), List.of()),
                clicked -> judge(clicked, finding.id(), Verdict.CONFIRMED, back));
        menu.put(Layout.TELEPORT, icon("teleport", messages.item(Message.MENU_TELEPORT), List.of()), clicked -> {
            clicked.closeInventory();
            Teleports.toFinding(clicked, finding, messages);
        });
        menu.put(Layout.DENY, icon("deny", messages.item(Message.MENU_FALSE_ALARM), List.of()),
                clicked -> judge(clicked, finding.id(), Verdict.FALSE_ALARM, back));
        menu.put(Layout.BACK, icon("list", messages.item(Message.MENU_BACK), List.of()),
                clicked -> openList(clicked, back));
        player.openInventory(menu.getInventory());
    }

    private List<Field> fields(Finding finding) {
        List<Field> fields = new ArrayList<>();
        fields.add(new Field(messages.plain(Message.MENU_FIELD_WORLD), finding.world()));
        fields.add(new Field(messages.plain(Message.MENU_FIELD_LOCATION), Messages.location(finding.box())));
        fields.add(new Field(messages.plain(Message.MENU_FIELD_DETECTED), messages.detected(finding.createdAt())));
        fields.add(new Field(messages.plain(Message.MENU_FIELD_PROBABILITY), Messages.probability(finding.score())));
        fields.add(new Field(messages.plain(Message.MENU_FIELD_SOURCE), messages.source(finding.source())));
        finding.review().ifPresent(review -> fields.add(new Field(messages.plain(review.verdict() == Verdict.CONFIRMED
                ? Message.MENU_FIELD_CONFIRMED : Message.MENU_FIELD_FALSE_ALARM), review.reviewer())));
        return fields;
    }

    private void judge(Player player, long id, Verdict verdict, ListPosition back) {
        deliver(player, vistructum.findings().review(id, verdict, player.getName()), reviewed -> reviewed.ifPresentOrElse(
                finding -> openList(player, back),
                () -> player.sendMessage(messages.chat(Message.COMMAND_FINDING_MISSING, number("id", id)))));
    }

    private CompletableFuture<Picture> scene(Finding finding) {
        World world = Bukkit.getWorld(finding.world());
        if (world == null) {
            return CompletableFuture.completedFuture(Scene.empty());
        }
        return Scene.capture(plugin, world, finding.box()).exceptionally(error -> Scene.empty());
    }

    private CompletableFuture<Builder> builder(Finding finding) {
        List<UUID> players = finding.players().stream().sorted().toList();
        if (players.isEmpty()) {
            return CompletableFuture.completedFuture(new Builder(messages.plain(Message.MENU_BUILDER_UNKNOWN),
                    Optional.empty()));
        }
        UUID first = players.getFirst();
        Optional<String> knownName = Optional.ofNullable(Bukkit.getOfflinePlayer(first).getName());
        return Faces.load(first, knownName).thenApplyAsync(profile -> {
            String name = profile.name().orElseGet(() -> messages.plain(Message.MENU_BUILDER_UNKNOWN));
            String shown = players.size() == 1 ? name : messages.plain(Message.MENU_BUILDER_MORE,
                    text("name", name), number("count", players.size() - 1L));
            return new Builder(shown, profile.face());
        }, mainThread);
    }

    private <T> void deliver(Player player, CompletableFuture<T> future, Consumer<T> then) {
        future.whenCompleteAsync((value, error) -> {
            if (!player.isOnline()) {
                return;
            }
            if (error != null) {
                Throwable cause = error instanceof CompletionException && error.getCause() != null
                        ? error.getCause() : error;
                player.sendMessage(messages.chat(Message.COMMAND_FAILED, text("reason",
                        Objects.requireNonNullElse(cause.getMessage(), cause.getClass().getSimpleName()))));
                return;
            }
            then.accept(value);
        }, mainThread);
    }

    private static ItemStack icon(String model, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(ResourcePack.FIRST_MODEL_DATA + ResourcePack.ICONS.indexOf(model));
        meta.displayName(name);
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack disabled(String model) {
        ItemStack item = icon(model, Component.empty(), List.of());
        ItemMeta meta = item.getItemMeta();
        meta.setHideTooltip(true);
        item.setItemMeta(meta);
        return item;
    }

    private record ListView(Page<Finding> page, long total) {
    }

    private record Builder(String name, Optional<Picture> face) {
    }

    private record DetailView(Finding finding, Picture scene, Builder builder) {
    }
}
