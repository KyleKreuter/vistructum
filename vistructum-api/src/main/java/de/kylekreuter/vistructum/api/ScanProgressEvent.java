package de.kylekreuter.vistructum.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/**
 * Fired after a scan has processed one tile.
 *
 * <p>The event is fired synchronously on the server main thread after the progress of the tile has been
 * committed, for successful and failed tiles alike. It is not fired for tiles whose processing completes after
 * the scan was cancelled.
 */
public final class ScanProgressEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ScanJob job;

    /**
     * Creates the event.
     *
     * @param job the scan after the tile was recorded
     * @throws NullPointerException if {@code job} is {@code null}
     */
    public ScanProgressEvent(ScanJob job) {
        this.job = Objects.requireNonNull(job, "job");
    }

    /**
     * Returns the scan after the tile was recorded.
     *
     * @return the scan with updated {@link ScanJob#tilesDone()}, {@link ScanJob#findings()} and
     *         {@link ScanJob#failures()}
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
