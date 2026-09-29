package de.kylekreuter.vistructum.core.recording;

import de.kylekreuter.vistructum.api.MotionFrame;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record MotionChunk(UUID player, String playerName, String world, List<MotionFrame> frames) {

    public MotionChunk {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(playerName, "playerName");
        Objects.requireNonNull(world, "world");
        frames = List.copyOf(frames);
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("motion chunk without frames");
        }
    }

    public long startMillis() {
        return frames.getFirst().atMillis();
    }

    public long endMillis() {
        return frames.getLast().atMillis();
    }
}
