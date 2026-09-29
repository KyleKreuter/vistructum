package de.kylekreuter.vistructum.api;

import java.time.Instant;
import java.util.Objects;

/**
 * Entry in the activity log of reviews and evidence links.
 *
 * <p>Only the current verdict of a finding is kept, so a replaced verdict no longer appears in the log.
 *
 * @param at time of the action
 * @param actor name of the party that acted, as supplied with the action
 * @param kind what was done
 * @param findingId identifier of the finding acted on
 */
public record Activity(Instant at, String actor, ActivityKind kind, long findingId) {

    /**
     * Validates the components.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public Activity {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(kind, "kind");
    }
}
