package de.kylekreuter.vistructum.ui.gui;

import de.kylekreuter.vistructum.api.BlockBox;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PictureTest {

    private static final int STONE = 0x707070;
    private static final int WOOL = 0xFFFFFF;
    private static final int SKY = 0xA4C2F4;

    @Test
    void uprightSymbolAlongZShowsItsFaceMirroredAsSeenFromNorth() {
        Map<String, Integer> blocks = Map.of("0,64,5", WOOL, "0,65,5", WOOL, "1,64,5", WOOL);
        Picture picture = Picture.side(blocks(blocks), new BlockBox(0, 64, 5, 1, 65, 5), View.ALONG_Z, 4);
        assertEquals(SKY, picture.at(1, 0));
        assertEquals(WOOL, picture.at(1, 1));
        assertEquals(WOOL, picture.at(2, 0));
        assertEquals(WOOL, picture.at(2, 1));
        assertEquals(SKY, picture.at(1, 2));
    }

    @Test
    void uprightSymbolAlongXShowsItsFaceAsSeenFromWest() {
        Map<String, Integer> blocks = Map.of("5,64,0", WOOL, "5,65,0", WOOL, "5,64,1", WOOL);
        Picture picture = Picture.side(blocks(blocks), new BlockBox(5, 64, 0, 5, 65, 1), View.ALONG_X, 4);
        assertEquals(WOOL, picture.at(1, 2));
        assertEquals(WOOL, picture.at(2, 2));
        assertEquals(WOOL, picture.at(2, 3));
        assertEquals(SKY, picture.at(1, 3));
    }

    @Test
    void wallBehindTheSymbolIsDarkened() {
        Map<String, Integer> blocks = Map.of("0,64,5", WOOL, "1,64,9", STONE);
        Picture picture = Picture.side(blocks(blocks), new BlockBox(0, 64, 5, 1, 65, 5), View.ALONG_Z, 4);
        assertEquals(WOOL, picture.at(2, 1));
        int darkened = (int) (0x70 * 180 / 255.0);
        assertEquals((darkened << 16) | (darkened << 8) | darkened, picture.at(2, 0));
    }

    @Test
    void surfaceIsShadedByTheHeightOfItsNorthernNeighbour() {
        Map<String, Integer> blocks = Map.of("0,64,0", STONE, "0,65,1", STONE, "1,64,1", STONE, "1,64,0", STONE);
        Picture picture = Picture.surface(blocks(blocks), 1, 1, 2);
        assertEquals(STONE, picture.at(1, 0));
        int even = (int) (0x70 * 220 / 255.0);
        assertEquals((even << 16) | (even << 8) | even, picture.at(1, 1));
    }

    private static Blocks blocks(Map<String, Integer> blocks) {
        return new Blocks() {
            @Override
            public int minHeight() {
                return -64;
            }

            @Override
            public int maxHeight() {
                return 320;
            }

            @Override
            public int surfaceY(int x, int z) {
                for (int y = maxHeight() - 1; y >= minHeight(); y--) {
                    if (!isEmpty(x, y, z)) {
                        return y;
                    }
                }
                return minHeight() - 1;
            }

            @Override
            public boolean isEmpty(int x, int y, int z) {
                return !blocks.containsKey(x + "," + y + "," + z);
            }

            @Override
            public int mapColor(int x, int y, int z) {
                return blocks.getOrDefault(x + "," + y + "," + z, 0);
            }
        };
    }
}
