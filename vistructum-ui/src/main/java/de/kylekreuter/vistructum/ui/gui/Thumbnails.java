package de.kylekreuter.vistructum.ui.gui;

import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.FindingCreatedEvent;
import de.kylekreuter.vistructum.api.Thumbnail;
import de.kylekreuter.vistructum.api.Vistructum;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;

public final class Thumbnails implements Listener {

    private static final int BATCH = 16;

    private final Plugin plugin;
    private final Vistructum vistructum;
    private final Executor mainThread;
    private final Executor background;
    private long cursor;
    private boolean running;
    private boolean pending;
    private int rendered;

    public Thumbnails(Plugin plugin, Vistructum vistructum) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.vistructum = Objects.requireNonNull(vistructum, "vistructum");
        this.mainThread = Bukkit.getScheduler().getMainThreadExecutor(plugin);
        this.background = task -> Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    public void fillMissing() {
        if (running) {
            pending = true;
            return;
        }
        running = true;
        rendered = 0;
        nextBatch();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFindingCreated(FindingCreatedEvent event) {
        fillMissing();
    }

    public CompletableFuture<Thumbnail> thumbnail(Finding finding) {
        return picture(finding).thenApply(Picture::thumbnail);
    }

    CompletableFuture<Picture> picture(Finding finding) {
        return vistructum.findings().thumbnail(finding.id()).thenCompose(stored -> stored
                .map(thumbnail -> CompletableFuture.completedFuture(Picture.of(thumbnail)))
                .orElseGet(() -> renderAndStore(finding)));
    }

    private void nextBatch() {
        pending = false;
        vistructum.findings().withoutThumbnail(cursor, BATCH).thenCompose(this::renderAll)
                .whenCompleteAsync((count, error) -> {
                    if (error != null) {
                        plugin.getLogger().log(Level.WARNING, "thumbnails cannot be listed", error);
                        finish();
                    } else if (count > 0 || pending) {
                        nextBatch();
                    } else {
                        finish();
                    }
                }, mainThread);
    }

    private void finish() {
        running = false;
        if (rendered > 0) {
            plugin.getLogger().info("rendered " + rendered + " finding thumbnails");
        }
    }

    private CompletableFuture<Integer> renderAll(List<Finding> findings) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (Finding finding : findings) {
            chain = chain.thenCompose(ignored -> renderAndStore(finding).handleAsync((picture, error) -> {
                cursor = Math.max(cursor, finding.id());
                if (error == null) {
                    rendered++;
                } else {
                    plugin.getLogger().warning("thumbnail of finding #" + finding.id() + " cannot be rendered: "
                            + Objects.requireNonNullElse(error.getMessage(), error.getClass().getSimpleName()));
                }
                return null;
            }, mainThread));
        }
        return chain.thenApply(ignored -> findings.size());
    }

    private CompletableFuture<Picture> renderAndStore(Finding finding) {
        World world = Bukkit.getWorld(finding.world());
        if (world == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("world " + finding.world()
                    + " is not loaded"));
        }
        return Scene.capture(plugin, world, finding.box(), mainThread, background).thenCompose(picture ->
                vistructum.findings().storeThumbnail(finding.id(), picture.thumbnail()).thenApply(stored -> picture));
    }
}
