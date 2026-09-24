package de.kylekreuter.vistructum.api;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FindingQueryTest {

    @Test
    void defaultsSelectEverythingNewestFirst() {
        FindingQuery all = FindingQuery.all();
        assertEquals(Optional.empty(), all.world());
        assertEquals(ReviewState.ANY, all.state());
        assertEquals(OptionalLong.empty(), all.beforeId());
        assertEquals(FindingQuery.DEFAULT_LIMIT, all.limit());
        assertEquals(ReviewState.OPEN, FindingQuery.open().state());
    }

    @Test
    void withersLeaveTheOriginalUntouched() {
        FindingQuery base = FindingQuery.open();
        FindingQuery narrowed = base.world("world").source(Source.FULLSCAN).since(Instant.EPOCH).before(9).limit(5);

        assertEquals(FindingQuery.open(), base);
        assertEquals(Optional.of("world"), narrowed.world());
        assertEquals(Optional.of(Source.FULLSCAN), narrowed.source());
        assertEquals(Optional.of(Instant.EPOCH), narrowed.since());
        assertEquals(OptionalLong.of(9), narrowed.beforeId());
        assertEquals(5, narrowed.limit());
        assertEquals(ReviewState.OPEN, narrowed.state());
    }

    @Test
    void limitOutsideRangeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> FindingQuery.all().limit(0));
        assertThrows(IllegalArgumentException.class, () -> FindingQuery.all().limit(FindingQuery.MAX_LIMIT + 1));
        assertThrows(NullPointerException.class, () -> FindingQuery.all().world(null));
    }
}
