package de.kylekreuter.vistructum.inference;

import ai.onnxruntime.OrtException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;

final class Occlusion {

    static final int SEARCH_STRIDE = 8;
    static final int PATCH = 8;
    static final int PATCH_STRIDE = 4;
    static final int MAX_VALUE = 255;

    private Occlusion() {
    }

    static int[] map(ModelKind kind, Scoring scoring, SurfaceScene scene, int top, int left, int bottom, int right)
            throws OrtException {
        FeatureMap padded = Features.padToGrid(kind, Features.extract(kind, scene));
        List<Integer> tops = origins(top, bottom, padded.rows());
        List<Integer> lefts = origins(left, right, padded.cols());
        WindowBatch search = windows(padded, tops, lefts);
        double[] scores = scoring.score(search, true, OptionalDouble.empty()).values();
        int best = 0;
        for (int i = 1; i < scores.length; i++) {
            if (scores[i] > scores[best]) {
                best = i;
            }
        }
        int length = search.windowLength();
        byte[] base = Arrays.copyOfRange(search.values(), best * length, (best + 1) * length);
        List<Integer> starts = new ArrayList<>();
        for (int start = 0; start <= Contract.GRID - PATCH; start += PATCH_STRIDE) {
            starts.add(start);
        }
        int variants = starts.size() * starts.size();
        byte[] occluded = new byte[variants * length];
        int[] rows = new int[variants];
        int[] cols = new int[variants];
        int variant = 0;
        for (int row : starts) {
            for (int col : starts) {
                System.arraycopy(base, 0, occluded, variant * length, length);
                hide(kind, search.channels(), occluded, variant * length, row, col);
                rows[variant] = row;
                cols[variant] = col;
                variant++;
            }
        }
        double[] hidden = scoring.score(new WindowBatch(search.channels(), rows, cols, occluded), true,
                OptionalDouble.empty()).values();
        int grid = Contract.GRID;
        double[] drop = new double[grid * grid];
        int[] count = new int[grid * grid];
        for (int i = 0; i < variants; i++) {
            double lost = Math.max(0.0, scores[best] - hidden[i]);
            for (int r = rows[i]; r < rows[i] + PATCH; r++) {
                for (int c = cols[i]; c < cols[i] + PATCH; c++) {
                    drop[r * grid + c] += lost;
                    count[r * grid + c]++;
                }
            }
        }
        int windowTop = search.tops()[best];
        int windowLeft = search.lefts()[best];
        int[] values = new int[scene.width() * scene.height()];
        for (int r = 0; r < grid && windowTop + r < scene.height(); r++) {
            for (int c = 0; c < grid && windowLeft + c < scene.width(); c++) {
                int cell = r * grid + c;
                double mean = drop[cell] / Math.max(count[cell], 1);
                values[(windowTop + r) * scene.width() + windowLeft + c] =
                        (int) Math.max(0, Math.min(MAX_VALUE, mean * MAX_VALUE));
            }
        }
        return values;
    }

    static List<Integer> origins(int start, int end, int limit) {
        int lastPossible = limit - Contract.GRID;
        int first = Math.max(0, Math.min(start, lastPossible));
        int last = Math.max(first, Math.min(end, limit) - Contract.GRID);
        List<Integer> origins = new ArrayList<>();
        for (int origin = first; origin <= last; origin += SEARCH_STRIDE) {
            origins.add(origin);
        }
        if (origins.getLast() != last) {
            origins.add(last);
        }
        return origins;
    }

    private static WindowBatch windows(FeatureMap padded, List<Integer> tops, List<Integer> lefts) {
        int grid = Contract.GRID;
        int count = tops.size() * lefts.size();
        int[] windowTops = new int[count];
        int[] windowLefts = new int[count];
        byte[] values = new byte[count * padded.channels() * grid * grid];
        int window = 0;
        for (int top : tops) {
            for (int left : lefts) {
                windowTops[window] = top;
                windowLefts[window] = left;
                for (int channel = 0; channel < padded.channels(); channel++) {
                    for (int row = 0; row < grid; row++) {
                        int source = (channel * padded.rows() + top + row) * padded.cols() + left;
                        int target = ((window * padded.channels() + channel) * grid + row) * grid;
                        System.arraycopy(padded.values(), source, values, target, grid);
                    }
                }
                window++;
            }
        }
        return new WindowBatch(padded.channels(), windowTops, windowLefts, values);
    }

    private static void hide(ModelKind kind, int channels, byte[] values, int offset, int row, int col) {
        int grid = Contract.GRID;
        for (int channel = 0; channel < channels; channel++) {
            byte neutral = (byte) kind.padValue(channel);
            for (int r = row; r < row + PATCH; r++) {
                int from = offset + (channel * grid + r) * grid + col;
                Arrays.fill(values, from, from + PATCH, neutral);
            }
        }
    }
}
