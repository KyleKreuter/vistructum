package de.kylekreuter.vistructum.ui.integration.discord;

import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.FindingCreatedEvent;
import de.kylekreuter.vistructum.api.FindingReviewedEvent;
import de.kylekreuter.vistructum.api.ScanFinishedEvent;
import de.kylekreuter.vistructum.api.Thumbnail;
import de.kylekreuter.vistructum.api.Vistructum;
import de.kylekreuter.vistructum.ui.gui.Thumbnails;
import de.kylekreuter.vistructum.ui.text.Messages;
import de.kylekreuter.vistructum.ui.web.WebSettings;
import de.kylekreuter.vistructum.ui.web.assets.Pngs;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public final class DiscordNotifier implements Listener, AutoCloseable {

    static final String SYSTEM = "discord";
    private static final int IMAGE_SCALE = 4;

    private final Vistructum vistructum;
    private final Thumbnails thumbnails;
    private final DiscordSettings settings;
    private final DiscordPayloads payloads;
    private final WebSettings web;
    private final DiscordWebhook webhook;
    private final Logger logger;

    public DiscordNotifier(Vistructum vistructum, Thumbnails thumbnails, DiscordSettings settings, Messages messages,
                           WebSettings web, Logger logger) {
        this.vistructum = Objects.requireNonNull(vistructum, "vistructum");
        this.thumbnails = Objects.requireNonNull(thumbnails, "thumbnails");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.payloads = new DiscordPayloads(messages);
        this.web = Objects.requireNonNull(web, "web");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.webhook = new DiscordWebhook(settings.webhook(), logger);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCreated(FindingCreatedEvent event) {
        Finding finding = event.getFinding();
        if (settings.created() && settings.announces(finding.score())) {
            announce(finding, builders(finding), settings.mentionRole());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onReviewed(FindingReviewedEvent event) {
        Finding finding = event.getFinding();
        if (!settings.reviewed() || !settings.announces(finding.score())) {
            return;
        }
        List<String> builders = builders(finding);
        vistructum.findings().reference(finding.id(), SYSTEM)
                .thenAccept(reference -> reference.ifPresentOrElse(
                        messageId -> update(finding, builders, messageId),
                        () -> announce(finding, builders, Optional.empty())))
                .exceptionally(error -> warn(finding, error));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onScanFinished(ScanFinishedEvent event) {
        if (settings.scanFinished()) {
            webhook.send(payloads.scanFinished(event.getJob()), Optional.empty());
        }
    }

    @Override
    public void close() {
        webhook.close();
    }

    private void announce(Finding finding, List<String> builders, Optional<String> mentionRole) {
        thumbnails.thumbnail(finding)
                .handle((thumbnail, error) -> Optional.ofNullable(thumbnail))
                .thenCompose(thumbnail -> webhook.send(
                        payloads.finding(finding, builders, link(finding), thumbnail.isPresent(), mentionRole),
                        thumbnail.map(picture -> image(finding, picture))))
                .thenCompose(messageId -> messageId
                        .map(id -> vistructum.findings().storeReference(finding.id(), SYSTEM, id))
                        .orElseGet(() -> CompletableFuture.completedFuture(false)))
                .exceptionally(error -> warn(finding, error));
    }

    private void update(Finding finding, List<String> builders, String messageId) {
        vistructum.findings().thumbnail(finding.id())
                .thenCompose(thumbnail -> webhook.edit(messageId, payloads.finding(finding, builders, link(finding),
                        thumbnail.isPresent(), settings.mentionRole())))
                .exceptionally(error -> warn(finding, error));
    }

    private Optional<String> link(Finding finding) {
        return web.enabled() ? Optional.of(web.publicUrl() + WebSettings.findingPath(finding.id())) : Optional.empty();
    }

    private static DiscordWebhook.Image image(Finding finding, Thumbnail thumbnail) {
        return new DiscordWebhook.Image(DiscordPayloads.imageName(finding.id()),
                Pngs.scaled(thumbnail.width(), thumbnail.height(), thumbnail.pixels(), IMAGE_SCALE));
    }

    private static List<String> builders(Finding finding) {
        return finding.players().stream()
                .map(player -> Bukkit.getOfflinePlayer(player).getName())
                .filter(Objects::nonNull)
                .sorted()
                .toList();
    }

    private <T> T warn(Finding finding, Throwable error) {
        logger.warning("the Discord notification of finding #" + finding.id() + " failed: "
                + Objects.requireNonNullElse(error.getMessage(), error.getClass().getSimpleName()));
        return null;
    }
}
