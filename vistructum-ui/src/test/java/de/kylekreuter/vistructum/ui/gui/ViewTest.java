package de.kylekreuter.vistructum.ui.gui;

import de.kylekreuter.vistructum.api.BlockBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ViewTest {

    @Test
    void flatSymbolIsSeenFromAbove() {
        assertEquals(View.TOP, View.of(new BlockBox(0, 64, 0, 8, 64, 8)));
    }

    @Test
    void uprightSymbolInTheXyPlaneIsSeenAlongZ() {
        assertEquals(View.ALONG_Z, View.of(new BlockBox(0, 64, 5, 8, 72, 5)));
    }

    @Test
    void uprightSymbolInTheZyPlaneIsSeenAlongX() {
        assertEquals(View.ALONG_X, View.of(new BlockBox(5, 64, 0, 5, 72, 8)));
    }

    @Test
    void planeSizeFollowsTheView() {
        BlockBox box = new BlockBox(5, 64, 0, 5, 72, 10);
        assertEquals(10, View.ALONG_X.planeWidth(box));
        assertEquals(8, View.ALONG_X.planeHeight(box));
    }
}
