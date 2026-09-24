package de.kylekreuter.vistructum.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/**
 * Fired after a verdict has been recorded for a finding.
 *
 * <p>The event is fired synchronously on the server main thread after the verdict has been committed, each time
 * {@link Findings#review(long, Verdict, String)} succeeds, including when an existing verdict is replaced.
 */
public final class FindingReviewedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Finding finding;

    /**
     * Creates the event.
     *
     * @param finding the reviewed finding
     * @throws NullPointerException if {@code finding} is {@code null}
     */
    public FindingReviewedEvent(Finding finding) {
        this.finding = Objects.requireNonNull(finding, "finding");
    }

    /**
     * Returns the reviewed finding.
     *
     * @return the finding as persisted, including the verdict just recorded
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
