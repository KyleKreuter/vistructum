package de.kylekreuter.vistructum.ui;

import de.kylekreuter.vistructum.api.ScanCause;
import de.kylekreuter.vistructum.api.ScanJob;
import de.kylekreuter.vistructum.api.ScanStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScanTextTest {

    @Test
    void completionIsTheShareOfCheckedTiles() {
        assertEquals(0.25, ScanText.completion(job(ScanStatus.RUNNING, 40, 10)));
    }

    @Test
    void completionIsZeroBeforeTheTilesArePlanned() {
        assertEquals(0.0, ScanText.completion(job(ScanStatus.RUNNING, 0, 0)));
    }

    @Test
    void aDoneScanIsComplete() {
        assertEquals(1.0, ScanText.completion(job(ScanStatus.DONE, 40, 39)));
    }

    private static ScanJob job(ScanStatus status, int total, int done) {
        return new ScanJob(7, "world", ScanCause.MANUAL, status, total, done, 0, 0, Instant.EPOCH);
    }
}
