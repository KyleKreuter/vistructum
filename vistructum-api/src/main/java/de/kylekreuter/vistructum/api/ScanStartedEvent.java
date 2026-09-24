package de.kylekreuter.vistructum.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/**
 * Fired when a scan has planned its tiles and begins processing them.
 *
 * <p>The event is fired synchronously on the server main thread, once per scan, when the scan changes from
 * {@link ScanStatus#QUEUED} to {@link ScanStatus#RUNNING}. A scan that resumes after a server restart does not fire
 * this event again.
 */
public final class ScanStartedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ScanJob job;

    /**
     * Creates the event.
     *
     * @param job the started scan
     * @throws NullPointerException if {@code job} is {@code null}
     */
    public ScanStartedEvent(ScanJob job) {
        this.job = Objects.requireNonNull(job, "job");
    }

    /**
     * Returns the started scan.
     *
     * @return the scan in state {@link ScanStatus#RUNNING}, with {@link ScanJob#tilesTotal()} set
     */
    public ScanJob getJob() {
        return job;
    }

    /**
     * Returns the handler list of this event instance.
     *
     * @return the handler list shared by all instances of this event type
     */
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * Returns the handler list of this event type, as required by the Bukkit event system.
     *
     * @return the handler list shared by all instances of this event type
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
