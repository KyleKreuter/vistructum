package de.kylekreuter.vistructum.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/**
 * Fired after a detection has been stored as a new finding.
 *
 * <p>The event is fired synchronously on the server main thread, once per stored finding, after the finding has
 * been committed and logged. It is not fired for candidates that were cancelled through
 * {@link FindingCreateEvent} or discarded as duplicates.
 *
 * @see FindingCreateEvent
 */
public final class FindingCreatedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Finding finding;

    /**
     * Creates the event.
     *
     * @param finding the stored finding
     * @throws NullPointerException if {@code finding} is {@code null}
     */
    public FindingCreatedEvent(Finding finding) {
        this.finding = Objects.requireNonNull(finding, "finding");
    }

    /**
     * Returns the stored finding.
     *
     * @return the finding as persisted, without a verdict
     */
    public Finding getFinding() {
        return finding;
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
