package de.kylekreuter.vistructum.ui;

import de.kylekreuter.vistructum.api.ScanFinishedEvent;
import de.kylekreuter.vistructum.api.ScanJob;
import de.kylekreuter.vistructum.api.ScanProgressEvent;
import de.kylekreuter.vistructum.api.ScanStartedEvent;
import de.kylekreuter.vistructum.api.ScanStatus;
import de.kylekreuter.vistructum.ui.gui.ProgressBar;
import de.kylekreuter.vistructum.ui.text.Message;
import de.kylekreuter.vistructum.ui.text.Messages;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

public final class ScanBossBar implements Listener {

    private static final long DONE_VISIBLE_TICKS = 100L;

    private final Plugin plugin;
    private final String staffPermission;
    private final Messages messages;
    private Shown shown;

    public ScanBossBar(Plugin plugin, String staffPermission, Messages messages) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.staffPermission = Objects.requireNonNull(staffPermission, "staffPermission");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStarted(ScanStartedEvent event) {
        show(event.getJob());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProgress(ScanProgressEvent event) {
        show(event.getJob());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFinished(ScanFinishedEvent event) {
        ScanJob job = event.getJob();
        if (job.status() == ScanStatus.DONE) {
            show(job);
            Bukkit.getScheduler().runTaskLater(plugin, () -> hide(job.id()), DONE_VISIBLE_TICKS);
        } else {
            hide(job.id());
        }
    }

    public void close() {
        if (shown != null) {
            Bukkit.getServer().hideBossBar(shown.bar());
            shown = null;
        }
    }

    private void show(ScanJob job) {
        if (shown == null || shown.jobId() != job.id()) {
            close();
            shown = new Shown(job.id(), BossBar.bossBar(Component.empty(), 0f, BossBar.Color.WHITE,
                    BossBar.Overlay.PROGRESS));
        }
        double completion = ScanText.completion(job);
        shown.bar().name(ProgressBar.title(label(job, completion), completion)).progress((float) completion);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(staffPermission)) {
                player.showBossBar(shown.bar());
            } else {
                player.hideBossBar(shown.bar());
            }
        }
    }

    private void hide(long jobId) {
        if (shown != null && shown.jobId() == jobId) {
            close();
        }
    }

    private Component label(ScanJob job, double completion) {
        return messages.chat(job.status() == ScanStatus.DONE ? Message.SCAN_BAR_DONE : Message.SCAN_BAR_PROGRESS,
                ScanText.values(job), Messages.text("percent", Messages.probability(completion)));
    }

    private record Shown(long jobId, BossBar bar) {
    }
}
