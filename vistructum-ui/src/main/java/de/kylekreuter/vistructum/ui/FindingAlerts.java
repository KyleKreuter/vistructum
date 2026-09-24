package de.kylekreuter.vistructum.ui;

import de.kylekreuter.vistructum.api.FindingCreatedEvent;
import de.kylekreuter.vistructum.api.FindingReviewedEvent;
import de.kylekreuter.vistructum.api.ScanFinishedEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Objects;

public final class FindingAlerts implements Listener {

    private final String staffPermission;

    public FindingAlerts(String staffPermission) {
        this.staffPermission = Objects.requireNonNull(staffPermission, "staffPermission");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCreated(FindingCreatedEvent event) {
        broadcast(ChatViews.alert(event.getFinding()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onReviewed(FindingReviewedEvent event) {
        event.getFinding().review().ifPresent(review -> broadcast(ChatViews.prefix().append(Component.text(
                "Fund #" + event.getFinding().id() + ": " + ChatViews.reviewText(review), NamedTextColor.GRAY))));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onScanFinished(ScanFinishedEvent event) {
        broadcast(ChatViews.prefix().append(Component.text(ChatViews.finished(event.getJob()), NamedTextColor.GRAY)));
    }

    private void broadcast(Component message) {
        Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.hasPermission(staffPermission))
                .forEach(player -> player.sendMessage(message));
    }
}
