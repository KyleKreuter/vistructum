package de.kylekreuter.vistructum.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/**
 * Fired when a scan reaches a final state.
 *
 * <p>The event is fired synchronously on the server main thread, once per scan, when the scan changes to
 * {@link ScanStatus#DONE}, {@link ScanStatus#FAILED} or, through {@link Scans#cancelAll()},
 * {@link ScanStatus#CANCELLED}. A scan interrupted by a storage failure does not reach a final state and does not
 * fire this event; it remains active and is resumed later.
 */
public final class ScanFinishedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ScanJob job;

    /**
     * Creates the event.
     *
     * @param job the finished scan
     * @throws NullPointerException if {@code job} is {@code null}
     */
    public ScanFinishedEvent(ScanJob job) {
        this.job = Objects.requireNonNull(job, "job");
    }

    /**
     * Returns the finished scan.
     *
     * @return the scan in its final state
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
