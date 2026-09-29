package de.kylekreuter.vistructum.core.recording;

import de.kylekreuter.vistructum.api.MotionFrame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MotionCodecTest {

    private static final long START = 1_790_000_000_000L;
    private static final int TICKS_PER_SECOND = 20;
    private static final int CHUNK_SECONDS = 5;

    @Test
    void roundTripKeepsFramesWithinQuantization() {
        List<MotionFrame> frames = building(new Random(7), CHUNK_SECONDS * TICKS_PER_SECOND);

        List<MotionFrame> decoded = MotionCodec.decode(MotionCodec.encode(frames));

        assertEquals(frames.size(), decoded.size());
        for (int i = 0; i < frames.size(); i++) {
            MotionFrame expected = frames.get(i);
            MotionFrame actual = decoded.get(i);
            assertEquals(expected.atMillis(), actual.atMillis());
            assertEquals(expected.x(), actual.x(), 0.5 / MotionCodec.POSITION_SCALE);
            assertEquals(expected.y(), actual.y(), 0.5 / MotionCodec.POSITION_SCALE);
            assertEquals(expected.z(), actual.z(), 0.5 / MotionCodec.POSITION_SCALE);
            assertEquals(expected.yaw(), actual.yaw(), 0.01f);
            assertEquals(expected.pitch(), actual.pitch(), 0.01f);
            assertEquals(expected.flags(), actual.flags());
            assertEquals(expected.mainHand(), actual.mainHand());
        }
    }

    @Test
    void emptyFramesRoundTrip() {
        assertEquals(List.of(), MotionCodec.decode(MotionCodec.encode(List.of())));
    }

    @Test
    void encodedSizePerPlayerSecondStaysSmall() {
        Random random = new Random(42);
        int chunks = 60;
        long bytes = 0;
        for (int i = 0; i < chunks; i++) {
            bytes += MotionCodec.encode(building(random, CHUNK_SECONDS * TICKS_PER_SECOND)).length;
        }
        double perPlayerSecond = bytes / (double) (chunks * CHUNK_SECONDS);
        System.out.printf("motion recording: %.1f bytes per player-second%n", perPlayerSecond);

        assertTrue(perPlayerSecond < 400, "encoded " + perPlayerSecond + " bytes per player-second");
    }

    private static List<MotionFrame> building(Random random, int count) {
        List<MotionFrame> frames = new ArrayList<>(count);
        double x = 100.5 + random.nextInt(50);
        double y = 64;
        double z = -200.5 + random.nextInt(50);
        float yaw = random.nextFloat() * 360 - 180;
        float pitch = 20;
        String hand = "minecraft:stone_bricks";
        for (int i = 0; i < count; i++) {
            boolean walking = (i / 20) % 2 == 0;
            if (walking) {
                x += Math.cos(Math.toRadians(yaw)) * 0.2158 + random.nextGaussian() * 0.001;
                z += Math.sin(Math.toRadians(yaw)) * 0.2158 + random.nextGaussian() * 0.001;
            }
            yaw += (float) (random.nextGaussian() * 3);
            pitch = Math.clamp(pitch + (float) (random.nextGaussian() * 2), -90f, 90f);
            if (random.nextInt(40) == 0) {
                y += 1;
            }
            if (random.nextInt(60) == 0) {
                hand = random.nextBoolean() ? "minecraft:oak_planks" : "minecraft:stone_bricks";
            }
            int flags = MotionFrame.ON_GROUND | (random.nextInt(6) == 0 ? MotionFrame.SWINGING : 0)
                    | (walking ? 0 : MotionFrame.SNEAKING);
            frames.add(new MotionFrame(START + i * 50L, x, y, z, yaw, pitch, flags, hand));
        }
        return frames;
    }
}
