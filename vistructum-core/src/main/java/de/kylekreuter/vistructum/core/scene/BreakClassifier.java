package de.kylekreuter.vistructum.core.scene;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class BreakClassifier {

    static final double MIN_SOLID_SHARE = 0.75;
    static final double MIN_SAME_MATERIAL_SHARE = 0.5;

    private BreakClassifier() {
    }

    public record Sample(boolean loaded, boolean solid, String material) {

        public static final Sample UNLOADED = new Sample(false, false, "");
    }

    public static Set<BlockPos> carved(Axis axis, Map<BlockPos, String> broken, Set<BlockPos> changed,
                                       Function<BlockPos, Sample> world) {
        int neighbours = 0;
        int solid = 0;
        int sameMaterial = 0;
        for (Map.Entry<BlockPos, String> entry : broken.entrySet()) {
            for (BlockPos neighbour : inPlaneNeighbours(axis, entry.getKey())) {
                if (changed.contains(neighbour)) {
                    continue;
                }
                Sample sample = world.apply(neighbour);
                if (!sample.loaded()) {
                    continue;
                }
                neighbours++;
                if (sample.solid()) {
                    solid++;
                    if (sample.material().equals(entry.getValue())) {
                        sameMaterial++;
                    }
                }
            }
        }
        boolean carved = neighbours > 0 && solid >= MIN_SOLID_SHARE * neighbours
                && sameMaterial >= MIN_SAME_MATERIAL_SHARE * solid;
        return carved ? Set.copyOf(broken.keySet()) : Set.of();
    }

    static BlockPos[] inPlaneNeighbours(Axis axis, BlockPos pos) {
        BlockPos[] neighbours = new BlockPos[8];
        int i = 0;
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) {
                    continue;
                }
                neighbours[i++] = switch (axis) {
                    case Y -> new BlockPos(pos.x() + a, pos.y(), pos.z() + b);
                    case Z -> new BlockPos(pos.x() + a, pos.y() + b, pos.z());
                    case X -> new BlockPos(pos.x(), pos.y() + b, pos.z() + a);
                };
            }
        }
        return neighbours;
    }
}
