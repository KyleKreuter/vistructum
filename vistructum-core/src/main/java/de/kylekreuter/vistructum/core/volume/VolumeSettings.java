package de.kylekreuter.vistructum.core.volume;

public record VolumeSettings(double fillerShare, int linkDistance, int minBlocks, int maxExtent, double prismFill,
                             int minSide) {

    public VolumeSettings {
        if (fillerShare <= 0 || fillerShare > 1) {
            throw new IllegalArgumentException("fillerShare must be in (0, 1], got " + fillerShare);
        }
        if (linkDistance < 1 || minBlocks < 1 || maxExtent < 1 || minSide < 1) {
            throw new IllegalArgumentException("linkDistance, minBlocks, maxExtent and minSide must be >= 1");
        }
        if (prismFill <= 0 || prismFill > 1) {
            throw new IllegalArgumentException("prismFill must be in (0, 1], got " + prismFill);
        }
    }
}
