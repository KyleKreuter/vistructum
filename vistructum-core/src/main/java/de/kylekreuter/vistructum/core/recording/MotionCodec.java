package de.kylekreuter.vistructum.core.recording;

import de.kylekreuter.vistructum.api.MotionFrame;
import de.kylekreuter.vistructum.core.store.Packed;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MotionCodec {

    static final double POSITION_SCALE = 4096.0;
    static final float ANGLE_SCALE = 100f;

    private MotionCodec() {
    }

    public static byte[] encode(List<MotionFrame> frames) {
        Packed.Writer out = new Packed.Writer();
        out.unsigned(frames.size());
        List<String> hands = new ArrayList<>();
        Map<String, Integer> handIndices = new HashMap<>();
        int[] handOf = new int[frames.size()];
        for (int i = 0; i < frames.size(); i++) {
            handOf[i] = handIndices.computeIfAbsent(frames.get(i).mainHand(), hand -> {
                hands.add(hand);
                return hands.size() - 1;
            });
        }
        out.unsigned(hands.size());
        hands.forEach(out::text);
        long previousTime = 0;
        for (MotionFrame frame : frames) {
            out.signed(frame.atMillis() - previousTime);
            previousTime = frame.atMillis();
        }
        writeDeltas(out, frames, frame -> position(frame.x()));
        writeDeltas(out, frames, frame -> position(frame.y()));
        writeDeltas(out, frames, frame -> position(frame.z()));
        writeDeltas(out, frames, frame -> angle(frame.yaw()));
        writeDeltas(out, frames, frame -> angle(frame.pitch()));
        for (MotionFrame frame : frames) {
            out.unsigned(frame.flags());
        }
        for (int hand : handOf) {
            out.unsigned(hand);
        }
        return Packed.deflate(out.bytes());
    }

    public static List<MotionFrame> decode(byte[] packed) {
        Packed.Reader in = new Packed.Reader(Packed.inflate(packed));
        int count = in.count();
        List<String> hands = new ArrayList<>();
        int handCount = in.count();
        for (int i = 0; i < handCount; i++) {
            hands.add(in.text());
        }
        long[] times = new long[count];
        long time = 0;
        for (int i = 0; i < count; i++) {
            time += in.signed();
            times[i] = time;
        }
        long[] x = readDeltas(in, count);
        long[] y = readDeltas(in, count);
        long[] z = readDeltas(in, count);
        long[] yaw = readDeltas(in, count);
        long[] pitch = readDeltas(in, count);
        int[] flags = new int[count];
        for (int i = 0; i < count; i++) {
            flags[i] = in.count();
        }
        List<MotionFrame> frames = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            frames.add(new MotionFrame(times[i], x[i] / POSITION_SCALE, y[i] / POSITION_SCALE, z[i] / POSITION_SCALE,
                    yaw[i] / ANGLE_SCALE, pitch[i] / ANGLE_SCALE, flags[i], hands.get(in.count())));
        }
        return frames;
    }

    static long position(double coordinate) {
        return Math.round(coordinate * POSITION_SCALE);
    }

    static long angle(float degrees) {
        return Math.round(degrees * ANGLE_SCALE);
    }

    private static void writeDeltas(Packed.Writer out, List<MotionFrame> frames, Quantity quantity) {
        long previous = 0;
        for (MotionFrame frame : frames) {
            long value = quantity.of(frame);
            out.signed(value - previous);
            previous = value;
        }
    }

    private static long[] readDeltas(Packed.Reader in, int count) {
        long[] values = new long[count];
        long value = 0;
        for (int i = 0; i < count; i++) {
            value += in.signed();
            values[i] = value;
        }
        return values;
    }

    @FunctionalInterface
    private interface Quantity {

        long of(MotionFrame frame);
    }
}
