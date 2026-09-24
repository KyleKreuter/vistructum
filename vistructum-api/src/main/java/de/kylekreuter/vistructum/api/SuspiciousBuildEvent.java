package de.kylekreuter.vistructum.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

public final class SuspiciousBuildEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Finding finding;

    public SuspiciousBuildEvent(Finding finding) {
        this.finding = Objects.requireNonNull(finding, "finding");
    }

    public Finding getFinding() {
        return finding;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
