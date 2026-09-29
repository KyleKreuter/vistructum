package de.kylekreuter.vistructum.api;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Movement of one player around a finding, recorded tick by tick.
 *
 * @param player identifier of the player
 * @param playerName account name of the player at the time of the recording
 * @param frames recorded ticks in ascending time order; ticks in which the player was offline or in another world
 *               are missing; the list is unmodifiable
 */
public record Recording(UUID player, String playerName, List<MotionFrame> frames) {

    /**
     * Validates the components and copies the frame list.
     *
     * @throws NullPointerException if a component or a frame is {@code null}
     */
    public Recording {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(playerName, "playerName");
        frames = List.copyOf(frames);
    }
}
