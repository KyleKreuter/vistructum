package de.kylekreuter.vistructum.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One block change recorded while players built the structure of a finding.
 *
 * @param at time at which the server processed the change
 * @param player identifier of the player that made the change
 * @param playerName account name of the player at the time of the change
 * @param action whether the block was placed or broken
 * @param x block x coordinate in the world
 * @param y block y coordinate in the world
 * @param z block z coordinate in the world
 * @param blockData block state in the string form of {@code BlockData#getAsString()}; for {@link BlockAction#PLACE}
 *                  the placed state, for {@link BlockAction#BREAK} the state that was broken
 */
public record BlockEvent(Instant at, UUID player, String playerName, BlockAction action, int x, int y, int z,
                         String blockData) {

    /**
     * Validates the components.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public BlockEvent {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(playerName, "playerName");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(blockData, "blockData");
    }
}
