package de.kylekreuter.vistructum.core.scene;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaskProjectorTest {

    private static final MaskProjector PROJECTOR = new MaskProjector(2);

    @Test
    void emptyInputYieldsNoProjections() {
        assertEquals(List.of(), PROJECTOR.project(List.of()));
    }

    @Test
    void flatGroundPlusIsOnlyLinesFromTheSide() {
        // a plus shape built flat at y=64, arms along x and z crossing at (10,64,10)
        List<BlockPos> plus = List.of(
                new BlockPos(8, 64, 10), new BlockPos(9, 64, 10), new BlockPos(10, 64, 10),
                new BlockPos(11, 64, 10), new BlockPos(12, 64, 10),
                new BlockPos(10, 64, 8), new BlockPos(10, 64, 9),
                new BlockPos(10, 64, 11), new BlockPos(10, 64, 12));

        List<Projection> projections = PROJECTOR.project(plus);
        Projection y = byAxis(projections, Axis.Y);
        Projection z = byAxis(projections, Axis.Z);
        Projection x = byAxis(projections, Axis.X);

        assertTrue(rowsWithModifiedCell(y).size() > 1, "top-down view should show the cross over several rows");
        assertTrue(colsWithModifiedCell(y).size() > 1, "top-down view should show the cross over several columns");

        assertEquals(1, rowsWithModifiedCell(z).size(), "a flat build collapses to a single row from the side");
        assertTrue(colsWithModifiedCell(z).size() > 1, "the side view still spans several columns");

        assertEquals(1, rowsWithModifiedCell(x).size(), "a flat build collapses to a single row from the side");
        assertTrue(colsWithModifiedCell(x).size() > 1, "the side view still spans several columns");
    }

    @Test
    void verticalWallPlusIsFullyVisibleFromTheFront() {
        // a plus shape built on a wall at z=5, arms along x and y crossing at (10,70,5)
        List<BlockPos> plus = List.of(
                new BlockPos(8, 70, 5), new BlockPos(9, 70, 5), new BlockPos(10, 70, 5),
                new BlockPos(11, 70, 5), new BlockPos(12, 70, 5),
                new BlockPos(10, 68, 5), new BlockPos(10, 69, 5),
                new BlockPos(10, 71, 5), new BlockPos(10, 72, 5));

        Projection z = byAxis(PROJECTOR.project(plus), Axis.Z);

        assertTrue(rowsWithModifiedCell(z).size() > 1, "the wall view should show the cross over several rows");
        assertTrue(colsWithModifiedCell(z).size() > 1, "the wall view should show the cross over several columns");

        // rows run from the highest y down: the top of the vertical arm (y=72, the cluster's max y) is row 0
        int margin = z.margin();
        int topRow = margin;
        int centerCol = margin + (10 - 8);
        assertEquals(1, z.scene().modified()[z.scene().index(topRow, centerCol)]);
    }

    @Test
    void marginSurroundsTheBoundingBoxOnEverySide() {
        int margin = 2;
        Projection y = byAxis(new MaskProjector(margin).project(List.of(new BlockPos(0, 0, 0))), Axis.Y);

        assertEquals(1 + 2 * margin, y.scene().width());
        assertEquals(1 + 2 * margin, y.scene().height());
        assertEquals(1, y.scene().modified()[y.scene().index(margin, margin)]);
        long set = 0;
        for (byte b : y.scene().modified()) {
            set += b;
        }
        assertEquals(1, set, "only the single block's cell should be set, the margin stays empty");
    }

    @Test
    void toWorldRoundTripsASingleBlock() {
        BlockPos block = new BlockPos(5, 10, 7);
        List<Projection> projections = PROJECTOR.project(List.of(block));

        Projection y = byAxis(projections, Axis.Y);
        int margin = y.margin();
        WorldBox fromY = y.toWorld(margin, margin, margin + 1, margin + 1);
        assertEquals(new WorldBox(5, 10, 7, 5, 10, 7), fromY);

        Projection z = byAxis(projections, Axis.Z);
        WorldBox fromZ = z.toWorld(margin, margin, margin + 1, margin + 1);
        assertEquals(new WorldBox(5, 10, 7, 5, 10, 7), fromZ);

        Projection x = byAxis(projections, Axis.X);
        WorldBox fromX = x.toWorld(margin, margin, margin + 1, margin + 1);
        assertEquals(new WorldBox(5, 10, 7, 5, 10, 7), fromX);
    }

    @Test
    void oversizedClusterSkipsTheProjectionsThatWouldExceedMaxSide() {
        List<BlockPos> stretched = List.of(new BlockPos(0, 0, 0), new BlockPos(1000, 5, 3));

        List<Projection> projections = PROJECTOR.project(stretched);

        assertEquals(1, projections.size());
        assertEquals(Axis.X, projections.get(0).axis());
    }

    private static Projection byAxis(List<Projection> projections, Axis axis) {
        return projections.stream().filter(p -> p.axis() == axis).findFirst()
                .orElseThrow(() -> new AssertionError("no projection for " + axis));
    }

    private static Set<Integer> rowsWithModifiedCell(Projection projection) {
        SurfaceScene scene = projection.scene();
        Set<Integer> rows = new java.util.HashSet<>();
        for (int row = 0; row < scene.height(); row++) {
            for (int col = 0; col < scene.width(); col++) {
                if (scene.modified()[scene.index(row, col)] == 1) {
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private static Set<Integer> colsWithModifiedCell(Projection projection) {
        SurfaceScene scene = projection.scene();
        return java.util.stream.IntStream.range(0, scene.width())
                .filter(col -> java.util.stream.IntStream.range(0, scene.height())
                        .anyMatch(row -> scene.modified()[scene.index(row, col)] == 1))
                .boxed().collect(Collectors.toSet());
    }
}
