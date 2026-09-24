package de.kylekreuter.vistructum.ui;

import de.kylekreuter.vistructum.api.Preview;
import de.kylekreuter.vistructum.api.ScanCause;
import de.kylekreuter.vistructum.api.ScanJob;
import de.kylekreuter.vistructum.api.ScanStatus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatViewsTest {

    @Test
    void smallPreviewKeepsEveryPixel() {
        List<Component> rows = ChatViews.preview(new Preview(3, 2, new byte[]{0, 50, 100, (byte) 150, (byte) 200, (byte) 250}));
        assertEquals(2, rows.size());
        assertEquals(3, rows.getFirst().children().size());
        assertEquals(TextColor.color(50, 50, 50), rows.getFirst().children().get(1).color());
    }

    @Test
    void largePreviewShrinksToAtMostThirtyTwoColumnsKeepingTheDarkestPixel() {
        byte[] pixels = new byte[64 * 64];
        Arrays.fill(pixels, (byte) 235);
        pixels[64 + 1] = 40;
        List<Component> rows = ChatViews.preview(new Preview(64, 64, pixels));
        assertEquals(32, rows.size());
        assertEquals(32, rows.getFirst().children().size());
        assertEquals(TextColor.color(40, 40, 40), rows.getFirst().children().getFirst().color());
    }

    @Test
    void scanJobShowsProgressOncePlanned() {
        ScanJob planning = new ScanJob(7, "world", ScanCause.DAILY, ScanStatus.QUEUED, 0, 0, 0, 0, Instant.EPOCH);
        ScanJob running = new ScanJob(7, "world", ScanCause.DAILY, ScanStatus.RUNNING, 10, 4, 2, 1, Instant.EPOCH);
        assertEquals("Scan #7 world: plant Kacheln", ChatViews.describe(planning));
        assertEquals("Scan #7 world: Kachel 4/10, 2 Funde, 1 Fehler", ChatViews.describe(running));
    }

    @Test
    void finishedScanNamesItsOutcome() {
        ScanJob cancelled = new ScanJob(3, "nether", ScanCause.MANUAL, ScanStatus.CANCELLED, 8, 2, 1, 0, Instant.EPOCH);
        assertEquals("Scan #3 nether abgebrochen: 1 Funde, 0 Fehler", ChatViews.finished(cancelled));
    }
}
