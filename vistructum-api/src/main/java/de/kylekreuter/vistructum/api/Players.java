package de.kylekreuter.vistructum.api;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Access to account data of the players that built a finding.
 *
 * <p>Faces are fetched from the Minecraft account services and stored with the findings. A stored face with a
 * skin is reused for 24 hours, a stored result without a skin for one hour. Players with an offline-mode
 * identifier are looked up by the name the server knows for them.
 *
 * <p>All returned futures complete on the server main thread as specified by {@link Vistructum}.
 *
 * @see Vistructum#players()
 * @see Finding#players()
 */
public interface Players {

    /**
     * Loads the face of a player.
     *
     * <p>If the account services cannot be reached, an outdated stored face is returned instead; without one, the
     * result carries no skin and nothing is stored, so the next call tries again.
     *
     * @param player identifier of the player
     * @return a future completing with the face; it completes exceptionally only if the storage fails
     * @throws NullPointerException if {@code player} is {@code null}
     */
    CompletableFuture<PlayerFace> face(UUID player);
}
