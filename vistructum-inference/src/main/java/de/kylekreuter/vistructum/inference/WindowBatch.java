package de.kylekreuter.vistructum.inference;

public record WindowBatch(int channels, int[] tops, int[] lefts, byte[] values) {

    public int size() {
        return tops.length;
    }

    public int windowLength() {
        return channels * Contract.GRID * Contract.GRID;
    }
}
