package de.kylekreuter.vistructum.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Front view of a player's head, taken from the skin registered with the player's Minecraft account.
 *
 * <p>The face is an 8 by 8 raster. Each pixel is an RGB colour in the form {@code 0xRRGGBB}, stored row by row
 * starting at the top-left corner. Where the hat layer of the skin is opaque, it replaces the base layer.
 *
 * @param player identifier of the player the face belongs to
 * @param name account name of the player, or an empty {@link Optional} if it is not known
 * @param pixels the {@value #SIZE} by {@value #SIZE} colours in row-major order, or an empty list if no skin is
 *               available for the player; the list is unmodifiable
 */
public record PlayerFace(UUID player, Optional<String> name, List<Integer> pixels) {

    /**
     * Number of pixels along each edge of a face.
     */
    public static final int SIZE = 8;

    /**
     * Validates the components and copies the pixel list.
     *
     * @throws NullPointerException if a component or a pixel is {@code null}
     * @throws IllegalArgumentException if {@code pixels} is neither empty nor of length {@code SIZE * SIZE}
     */
    public PlayerFace {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(name, "name");
        pixels = List.copyOf(pixels);
        if (!pixels.isEmpty() && pixels.size() != SIZE * SIZE) {
            throw new IllegalArgumentException("face with " + pixels.size() + " pixels");
        }
    }

    /**
     * Reports whether a skin was available for the player.
     *
     * @return {@code true} if {@link #pixels()} holds the face, {@code false} if it is empty
     */
    public boolean hasSkin() {
        return !pixels.isEmpty();
    }
}
