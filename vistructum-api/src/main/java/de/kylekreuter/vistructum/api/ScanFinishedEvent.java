package de.kylekreuter.vistructum.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

public final class ScanFinishedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ScanJob job;

    public ScanFinishedEvent(ScanJob job) {
        this.job = Objects.requireNonNull(job, "job");
    }

    public ScanJob getJob() {
        return job;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
