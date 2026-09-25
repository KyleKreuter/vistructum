package de.kylekreuter.vistructum.inference;

public record FeatureMap(int channels, int rows, int cols, byte[] values) {

    public FeatureMap {
        if (values.length != channels * rows * cols) {
            throw new IllegalArgumentException("feature map has " + values.length + " values, expected "
                    + channels * rows * cols);
        }
    }

    int get(int channel, int row, int col) {
        return values[(channel * rows + row) * cols + col] & 0xFF;
    }
}
