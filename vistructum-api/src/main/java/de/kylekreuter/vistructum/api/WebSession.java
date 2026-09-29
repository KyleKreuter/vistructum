package de.kylekreuter.vistructum.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Signed-in session of a staff member in the web application.
 *
 * @param player identifier of the player the session was issued to
 * @param playerName account name of the player when the login link was issued; it is stored as the reviewer name
 * @param expiresAt time after which the session is no longer accepted
 */
public record WebSession(UUID player, String playerName, Instant expiresAt) {

    /**
     * Validates the components.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public WebSession {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(playerName, "playerName");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
