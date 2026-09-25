package de.kylekreuter.vistructum.inference;

import ai.onnxruntime.OnnxJavaType;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.IntStream;

final class Scoring {

    static final int BATCH_SIZE = 64;
    static final int VIEWS = 8;

    private final OrtEnvironment environment;
    private final OrtSession session;

    Scoring(OrtEnvironment environment, OrtSession session) {
        this.environment = environment;
        this.session = session;
    }

    Scores score(WindowBatch batch, boolean tta, OptionalDouble prefilter) throws OrtException {
        if (!tta) {
            return new Scores(singleView(batch, all(batch.size())), 0);
        }
        if (prefilter.isEmpty()) {
            return new Scores(allViews(batch, all(batch.size())), batch.size());
        }
        double[] scores = singleView(batch, all(batch.size()));
        int[] passed = IntStream.range(0, scores.length)
                .filter(i -> scores[i] >= prefilter.getAsDouble()).toArray();
        if (passed.length > 0) {
            double[] refined = allViews(batch, passed);
            for (int i = 0; i < passed.length; i++) {
                scores[passed[i]] = refined[i];
            }
        }
        return new Scores(scores, passed.length);
    }

    private static int[] all(int count) {
        return IntStream.range(0, count).toArray();
    }

    private double[] singleView(WindowBatch batch, int[] windows) throws OrtException {
        double[] scores = new double[windows.length];
        int length = batch.windowLength();
        for (int start = 0; start < windows.length; start += BATCH_SIZE) {
            int count = Math.min(BATCH_SIZE, windows.length - start);
            ByteBuffer input = ByteBuffer.allocateDirect(count * length);
            for (int i = 0; i < count; i++) {
                input.put(batch.values(), windows[start + i] * length, length);
            }
            float[] positive = run(input.flip(), count, batch.channels());
            for (int i = 0; i < count; i++) {
                scores[start + i] = positive[i];
            }
        }
        return scores;
    }

    private double[] allViews(WindowBatch batch, int[] windows) throws OrtException {
        double[] scores = new double[windows.length];
        int step = Math.max(1, BATCH_SIZE / VIEWS);
        int length = batch.windowLength();
        for (int start = 0; start < windows.length; start += step) {
            int count = Math.min(step, windows.length - start);
            ByteBuffer input = ByteBuffer.allocateDirect(VIEWS * count * length);
            for (int view = 0; view < VIEWS; view++) {
                for (int i = 0; i < count; i++) {
                    putView(input, batch, windows[start + i], view);
                }
            }
            float[] positive = run(input.flip(), VIEWS * count, batch.channels());
            for (int i = 0; i < count; i++) {
                float sum = 0f;
                for (int view = 0; view < VIEWS; view++) {
                    sum += positive[view * count + i];
                }
                scores[start + i] = sum / VIEWS;
            }
        }
        return scores;
    }

    private static void putView(ByteBuffer input, WindowBatch batch, int window, int view) {
        int grid = Contract.GRID;
        byte[] values = batch.values();
        for (int channel = 0; channel < batch.channels(); channel++) {
            int base = (window * batch.channels() + channel) * grid * grid;
            for (int row = 0; row < grid; row++) {
                for (int col = 0; col < grid; col++) {
                    input.put(values[base + viewSource(view, row, col)]);
                }
            }
        }
    }

    static int viewSource(int view, int row, int col) {
        int last = Contract.GRID - 1;
        int flippedRow = (view & 2) != 0 ? last - row : row;
        int flippedCol = (view & 1) != 0 ? last - col : col;
        return view < 4
                ? flippedRow * Contract.GRID + flippedCol
                : flippedCol * Contract.GRID + flippedRow;
    }

    private float[] run(ByteBuffer input, int count, int channels) throws OrtException {
        long[] shape = {count, channels, Contract.GRID, Contract.GRID};
        try (OnnxTensor tensor = OnnxTensor.createTensor(environment, input, shape, OnnxJavaType.UINT8);
             OrtSession.Result result = session.run(Map.of(Contract.INPUT_NAME, tensor))) {
            FloatBuffer output = ((OnnxTensor) result.get(Contract.OUTPUT_NAME).orElseThrow()).getFloatBuffer();
            int labels = Contract.LABELS.size();
            float[] positive = new float[count];
            for (int i = 0; i < count; i++) {
                positive[i] = output.get(i * labels + Contract.POSITIVE);
            }
            return positive;
        }
    }

    record Scores(double[] values, int refined) {
    }
}
