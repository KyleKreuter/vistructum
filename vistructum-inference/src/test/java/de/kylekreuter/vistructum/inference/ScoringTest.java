package de.kylekreuter.vistructum.inference;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoringTest {

    private static final int LAST = Contract.GRID - 1;

    @Test
    void viewsFollowTheOrderOfThePythonD4Views() {
        assertEquals(index(2, 5), Scoring.viewSource(0, 2, 5));
        assertEquals(index(2, LAST - 5), Scoring.viewSource(1, 2, 5));
        assertEquals(index(LAST - 2, 5), Scoring.viewSource(2, 2, 5));
        assertEquals(index(LAST - 2, LAST - 5), Scoring.viewSource(3, 2, 5));
        assertEquals(index(5, 2), Scoring.viewSource(4, 2, 5));
        assertEquals(index(LAST - 5, 2), Scoring.viewSource(5, 2, 5));
        assertEquals(index(5, LAST - 2), Scoring.viewSource(6, 2, 5));
        assertEquals(index(LAST - 5, LAST - 2), Scoring.viewSource(7, 2, 5));
    }

    @Test
    void everyViewIsAPermutationOfTheWindow() {
        for (int view = 0; view < Scoring.VIEWS; view++) {
            Set<Integer> sources = new HashSet<>();
            for (int row = 0; row < Contract.GRID; row++) {
                for (int col = 0; col < Contract.GRID; col++) {
                    sources.add(Scoring.viewSource(view, row, col));
                }
            }
            assertEquals(Contract.GRID * Contract.GRID, sources.size());
        }
    }

    private static int index(int row, int col) {
        return row * Contract.GRID + col;
    }
}
