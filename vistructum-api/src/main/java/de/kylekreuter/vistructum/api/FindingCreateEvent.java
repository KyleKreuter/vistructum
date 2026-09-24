package de.kylekreuter.vistructum.api;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/**
 * Fired before a detection is stored as a finding; cancelling it prevents the finding.
 *
 * <p>The event is fired synchronously on the server main thread after the candidate has passed the duplicate
 * check and before it is written to storage. A candidate is a duplicate if a finding in the same world with an
 * overlapping {@link BlockBox} was created within the deduplication window configured in the core plugin;
 * duplicates never fire this event.
 *
 * <p>Cancelling the event discards the candidate permanently. It is not stored, not retried and does not fire a
 * {@link FindingCreatedEvent}; the core plugin logs the cancellation. If the event is not cancelled, the
 * duplicate check is repeated atomically with the write, so a finding created concurrently at an overlapping
 * position can still cause the candidate to be discarded without a {@link FindingCreatedEvent}.
 *
 * @see FindingCreatedEvent
 */
public final class FindingCreateEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final FindingCandidate candidate;
    private boolean cancelled;

    /**
     * Creates the event.
     *
     * @param candidate the detection about to be stored
     * @throws NullPointerException if {@code candidate} is {@code null}
     */
    public FindingCreateEvent(FindingCandidate candidate) {
        this.candidate = Objects.requireNonNull(candidate, "candidate");
    }

    /**
     * Returns the detection about to be stored.
     *
     * @return the candidate, never {@code null}
     */
    public FindingCandidate getCandidate() {
        return candidate;
    }

    /**
     * Reports whether the candidate will be discarded.
     *
     * @return {@code true} if a listener has cancelled the event
     */
    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Sets whether the candidate is to be discarded.
     *
     * @param cancel {@code true} to discard the candidate, {@code false} to let it be stored
     */
    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
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
