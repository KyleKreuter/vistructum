package de.kylekreuter.vistructum.inference;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeaturesTest {

    @Test
    void placesWindowOriginsOnTheStrideAndCoversTheEdge() {
        assertEquals(List.of(0), Features.origins(10));
        assertEquals(List.of(0), Features.origins(64));
        assertEquals(List.of(0, 24, 36), Features.origins(100));
        assertEquals(List.of(0, 24, 48), Features.origins(112));
    }

    @Test
    void padsSmallScenesWithTheKindPadValues() {
        SurfaceScene scene = new SurfaceScene(2, 1, new short[]{1, 1}, new short[]{5, 5}, new byte[]{10, 20},
                new byte[]{1, 0});

        WindowBatch batch = Features.windows(ModelKind.FULLSCAN, Features.extract(ModelKind.FULLSCAN, scene));

        assertEquals(1, batch.size());
        int plane = Contract.GRID * Contract.GRID;
        assertEquals(4, batch.values()[0]);
        assertEquals(4, batch.values()[plane - 1]);
        assertEquals(0, batch.values()[plane + plane - 1]);
        assertEquals(10, batch.values()[2 * plane]);
        assertEquals((byte) 128, batch.values()[3 * plane - 1]);
    }

    @Test
    void relativeHeightIsTheClippedDistanceToTheMedianGround() {
        int size = 9;
        short[] heights = new short[size * size];
        heights[4 * size + 4] = 10;
        short[] blocks = new short[size * size];
        blocks[0] = SurfaceScene.UNKNOWN;
        SurfaceScene scene = new SurfaceScene(size, size, blocks, heights, new byte[size * size], new byte[size * size]);

        byte[] relative = Features.relativeHeight(scene);

        assertEquals(4, relative[0]);
        assertEquals(8, relative[4 * size + 4]);
        assertEquals(4, relative[4 * size + 5]);
    }
}
