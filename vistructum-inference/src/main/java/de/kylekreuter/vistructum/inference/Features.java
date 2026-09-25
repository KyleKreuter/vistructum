package de.kylekreuter.vistructum.inference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Features {

    private static final int HEIGHT_ZERO = Contract.HEIGHT_CLIP;

    private Features() {
    }

    public static FeatureMap extract(ModelKind kind, SurfaceScene scene) {
        return switch (kind) {
            case MASK -> new FeatureMap(1, scene.height(), scene.width(), scene.modified().clone());
            case FULLSCAN -> fullscan(scene);
        };
    }

    public static WindowBatch windows(ModelKind kind, FeatureMap features) {
        FeatureMap padded = padToGrid(kind, features);
        List<Integer> rowOrigins = origins(padded.rows());
        List<Integer> colOrigins = origins(padded.cols());
        int count = rowOrigins.size() * colOrigins.size();
        int grid = Contract.GRID;
        int[] tops = new int[count];
        int[] lefts = new int[count];
        byte[] values = new byte[count * padded.channels() * grid * grid];
        int window = 0;
        for (int top : rowOrigins) {
            for (int left : colOrigins) {
                tops[window] = top;
                lefts[window] = left;
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
        return new WindowBatch(padded.channels(), tops, lefts, values);
    }

    static List<Integer> origins(int length) {
        int grid = Contract.GRID;
        List<Integer> origins = new ArrayList<>();
        if (length <= grid) {
            origins.add(0);
            return origins;
        }
        for (int origin = 0; origin <= length - grid; origin += Contract.STRIDE) {
            origins.add(origin);
        }
        if (origins.getLast() != length - grid) {
            origins.add(length - grid);
        }
        return origins;
    }

    private static FeatureMap padToGrid(ModelKind kind, FeatureMap features) {
        int rows = Math.max(features.rows(), Contract.GRID);
        int cols = Math.max(features.cols(), Contract.GRID);
        if (rows == features.rows() && cols == features.cols()) {
            return features;
        }
        byte[] values = new byte[features.channels() * rows * cols];
        for (int channel = 0; channel < features.channels(); channel++) {
            Arrays.fill(values, channel * rows * cols, (channel + 1) * rows * cols, (byte) kind.padValue(channel));
            for (int row = 0; row < features.rows(); row++) {
                System.arraycopy(features.values(), (channel * features.rows() + row) * features.cols(), values,
                        (channel * rows + row) * cols, features.cols());
            }
        }
        return new FeatureMap(features.channels(), rows, cols, values);
    }

    private static FeatureMap fullscan(SurfaceScene scene) {
        int count = scene.width() * scene.height();
        byte[] values = new byte[4 * count];
        byte[] relative = relativeHeight(scene);
        byte[] boundaries = blockBoundaries(scene);
        byte[] steps = heightSteps(scene);
        for (int i = 0; i < count; i++) {
            values[i] = relative[i];
            values[count + i] = boundaries[i];
            values[2 * count + i] = known(scene, i) ? scene.luminance()[i] : (byte) Contract.LUMINANCE_PAD;
            values[3 * count + i] = steps[i];
        }
        return new FeatureMap(4, scene.height(), scene.width(), values);
    }

    private static boolean known(SurfaceScene scene, int index) {
        return scene.blocks()[index] != SurfaceScene.UNKNOWN;
    }

    static byte[] relativeHeight(SurfaceScene scene) {
        int rows = scene.height();
        int cols = scene.width();
        int reach = Contract.HEIGHT_CONTEXT / 2;
        byte[] out = new byte[rows * cols];
        double[] samples = new double[(2 * reach / Contract.HEIGHT_SAMPLE_STEP + 1)
                * (2 * reach / Contract.HEIGHT_SAMPLE_STEP + 1)];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int index = scene.index(row, col);
                if (!known(scene, index)) {
                    out[index] = (byte) HEIGHT_ZERO;
                    continue;
                }
                int valid = 0;
                for (int dr = -reach; dr <= reach; dr += Contract.HEIGHT_SAMPLE_STEP) {
                    for (int dc = -reach; dc <= reach; dc += Contract.HEIGHT_SAMPLE_STEP) {
                        int sample = scene.index(clamp(row + dr, rows), clamp(col + dc, cols));
                        if (known(scene, sample)) {
                            samples[valid++] = scene.heights()[sample];
                        }
                    }
                }
                double height = scene.heights()[index];
                double ground = valid == 0 ? height : median(samples, valid);
                double relative = Math.max(-Contract.HEIGHT_CLIP, Math.min(Contract.HEIGHT_CLIP, Math.rint(height - ground)));
                out[index] = (byte) (relative + HEIGHT_ZERO);
            }
        }
        return out;
    }

    private static int clamp(int value, int length) {
        return Math.max(0, Math.min(length - 1, value));
    }

    private static double median(double[] samples, int count) {
        Arrays.sort(samples, 0, count);
        int middle = count / 2;
        return count % 2 == 1 ? samples[middle] : (samples[middle - 1] + samples[middle]) / 2.0;
    }

    static byte[] blockBoundaries(SurfaceScene scene) {
        int rows = scene.height();
        int cols = scene.width();
        short[] blocks = scene.blocks();
        boolean[] edge = new boolean[rows * cols];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int index = scene.index(row, col);
                if (row + 1 < rows && blocks[index] != blocks[index + cols]) {
                    edge[index] = true;
                    edge[index + cols] = true;
                }
                if (col + 1 < cols && blocks[index] != blocks[index + 1]) {
                    edge[index] = true;
                    edge[index + 1] = true;
                }
            }
        }
        byte[] out = new byte[rows * cols];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) (edge[i] && known(scene, i) ? 1 : 0);
        }
        return out;
    }

    static byte[] heightSteps(SurfaceScene scene) {
        int rows = scene.height();
        int cols = scene.width();
        short[] heights = scene.heights();
        byte[] out = new byte[rows * cols];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int index = scene.index(row, col);
                if (row + 1 < rows && heights[index] != heights[index + cols] && known(scene, index)
                        && known(scene, index + cols)) {
                    out[index] = 1;
                    out[index + cols] = 1;
                }
                if (col + 1 < cols && heights[index] != heights[index + 1] && known(scene, index)
                        && known(scene, index + 1)) {
                    out[index] = 1;
                    out[index + 1] = 1;
                }
            }
        }
        return out;
    }
}
