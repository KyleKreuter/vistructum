package de.kylekreuter.vistructum.api;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

public final class FindingCreateEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final FindingCandidate candidate;
    private boolean cancelled;

    public FindingCreateEvent(FindingCandidate candidate) {
        this.candidate = Objects.requireNonNull(candidate, "candidate");
    }

    public FindingCandidate getCandidate() {
        return candidate;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
