package de.kylekreuter.vistructum.api;

import java.time.Instant;
import java.util.Objects;

/**
 * Active public link to the evidence of a finding.
 *
 * <p>The token is permanent for its finding: deactivating and activating the link again keeps it.
 *
 * @param token share token that forms the last part of the public link
 * @param sharedSince time the link was activated most recently
 */
public record EvidenceShare(String token, Instant sharedSince) {

    /**
     * Validates the components.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public EvidenceShare {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(sharedSince, "sharedSince");
    }
}
