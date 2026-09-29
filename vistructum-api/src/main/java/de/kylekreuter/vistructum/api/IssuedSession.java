package de.kylekreuter.vistructum.api;

import java.util.Objects;

/**
 * Session created by redeeming a login token, together with the secret that identifies it.
 *
 * <p>The token is returned only here. The server keeps a hash of it, so a lost token cannot be recovered.
 *
 * @param token secret session token to be handed to the browser
 * @param session the created session
 */
public record IssuedSession(String token, WebSession session) {

    /**
     * Validates the components.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public IssuedSession {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(session, "session");
    }

    /**
     * Describes the session without revealing the token.
     *
     * @return a short description
     */
    @Override
    public String toString() {
        return "IssuedSession[" + session + "]";
    }
}
