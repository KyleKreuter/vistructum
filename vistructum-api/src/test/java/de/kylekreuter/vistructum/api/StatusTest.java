package de.kylekreuter.vistructum.api;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StatusTest {

    @Test
    void unreachableSidecarCarriesTheError() {
        SidecarStatus sidecar = SidecarStatus.unreachable("connection refused");
        assertFalse(sidecar.reachable());
        assertEquals(Optional.of("connection refused"), sidecar.error());
        assertEquals(Map.of(), sidecar.models());
    }

    @Test
    void statusListsAreImmutableCopies() {
        List<ScanJob> jobs = new ArrayList<>(List.of(new ScanJob(1, "world", ScanCause.MANUAL, ScanStatus.RUNNING, 4, 1,
                0, 0, Instant.EPOCH)));
        VistructumStatus status = new VistructumStatus(3, 2, jobs, SidecarStatus.unreachable("down"));
        jobs.clear();
        assertEquals(1, status.activeScans().size());
        assertThrows(UnsupportedOperationException.class, () -> status.activeScans().clear());
    }
}
