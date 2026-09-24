package de.kylekreuter.vistructum.ui;

import de.kylekreuter.vistructum.api.SuspiciousBuildEvent;
import net.kyori.adventure.text.Component;
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
    public void onFinding(SuspiciousBuildEvent event) {
        Component message = ChatViews.alert(event.getFinding());
        Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.hasPermission(staffPermission))
                .forEach(player -> player.sendMessage(message));
    }
}
