package de.kylekreuter.vistructum.api;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Full skin texture registered with a player's Minecraft account.
 *
 * <p>The array is copied on construction and on every access, so a skin is immutable.
 *
 * @param player identifier of the player the skin belongs to
 * @param name account name of the player, or an empty {@link Optional} if it is not known
 * @param png the skin texture as a PNG image of 64 by 64 pixels
 * @param slim {@code true} if the skin uses the slim arm model, {@code false} for the classic model
 */
public record PlayerSkin(UUID player, Optional<String> name, byte[] png, boolean slim) {

    /**
     * Validates the components and copies the image.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if {@code png} is empty
     */
    public PlayerSkin {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(png, "png");
        if (png.length == 0) {
            throw new IllegalArgumentException("empty skin image");
        }
        png = png.clone();
    }

    /**
     * Returns a copy of the image.
     *
     * @return a new array holding the PNG bytes that the caller may modify freely
     */
    @Override
    public byte[] png() {
        return png.clone();
    }

    /**
     * Compares the skin with another object by value, including the image bytes.
     *
     * @param other object to compare with
     * @return {@code true} if {@code other} is a skin with equal components and equal image bytes
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof PlayerSkin that && player.equals(that.player) && name.equals(that.name)
                && slim == that.slim && Arrays.equals(png, that.png);
    }

    /**
     * Computes a hash code from the components and the image bytes.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(player, name, slim) * 31 + Arrays.hashCode(png);
    }

    /**
     * Describes the skin without listing the image bytes.
     *
     * @return a short description
     */
    @Override
    public String toString() {
        return "PlayerSkin[" + player + ", " + name.orElse("?") + ", " + png.length + " bytes"
                + (slim ? ", slim" : "") + "]";
    }
}
