package de.kylekreuter.vistructum.inference;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClustersTest {

    @Test
    void mergesOverlappingHitWindowsAndSortsByScore() {
        WindowBatch batch = new WindowBatch(1, new int[]{0, 0, 24, 200, 400}, new int[]{0, 24, 0, 200, 400}, new byte[0]);
        double[] scores = {0.97, 0.99, 0.2, 0.995, 0.98};

        List<Detection> detections = Clusters.all(batch, scores, 0.96);

        assertEquals(List.of(
                new Detection(200, 200, 264, 264, 0.995, 1),
                new Detection(0, 0, 64, 88, 0.99, 2),
                new Detection(400, 400, 464, 464, 0.98, 1)), detections);
        assertEquals(List.of(new Detection(0, 0, 64, 88, 0.99, 2)), Clusters.flagged(batch, scores, 0.96, 2));
    }

    @Test
    void chainsWindowsThatOnlyTouchThroughANeighbour() {
        WindowBatch batch = new WindowBatch(1, new int[]{0, 0, 0}, new int[]{0, 48, 96}, new byte[0]);

        List<Detection> detections = Clusters.all(batch, new double[]{0.99, 0.99, 0.99}, 0.5);

        assertEquals(List.of(new Detection(0, 0, 64, 160, 0.99, 3)), detections);
    }
}
