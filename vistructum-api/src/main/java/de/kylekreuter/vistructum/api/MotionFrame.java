package de.kylekreuter.vistructum.api;

import java.util.Objects;

/**
 * State of one player in one server tick, as recorded for the replay of a finding.
 *
 * @param atMillis time of the tick in milliseconds since the epoch
 * @param x x coordinate of the player's feet in the world
 * @param y y coordinate of the player's feet in the world
 * @param z z coordinate of the player's feet in the world
 * @param yaw body rotation around the vertical axis in degrees, as reported by {@code Location#getYaw()}
 * @param pitch head rotation around the horizontal axis in degrees, as reported by {@code Location#getPitch()}
 * @param flags combination of the flag constants of this type
 * @param mainHand namespaced key of the item held in the main hand, or an empty string if the hand is empty
 */
public record MotionFrame(long atMillis, double x, double y, double z, float yaw, float pitch, int flags,
                          String mainHand) {

    /**
     * Flag set while the player stands on a block.
     */
    public static final int ON_GROUND = 1;

    /**
     * Flag set while the player sneaks.
     */
    public static final int SNEAKING = 1 << 1;

    /**
     * Flag set while the player sprints.
     */
    public static final int SPRINTING = 1 << 2;

    /**
     * Flag set while the player swims.
     */
    public static final int SWIMMING = 1 << 3;

    /**
     * Flag set while the player glides with an elytra.
     */
    public static final int GLIDING = 1 << 4;

    /**
     * Flag set in the tick in which the player swung the main arm.
     */
    public static final int SWINGING = 1 << 5;

    /**
     * Validates the components.
     *
     * @throws NullPointerException if {@code mainHand} is {@code null}
     */
    public MotionFrame {
        Objects.requireNonNull(mainHand, "mainHand");
    }

    /**
     * Reports whether a flag is set.
     *
     * @param flag one of the flag constants of this type
     * @return {@code true} if every bit of {@code flag} is set in {@link #flags()}
     */
    public boolean has(int flag) {
        return (flags & flag) == flag;
    }
}
