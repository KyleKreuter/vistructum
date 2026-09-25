package de.kylekreuter.vistructum.ui.gui;

import de.kylekreuter.vistructum.api.BlockBox;
import org.bukkit.Color;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PictureTest {

    private static final int STONE = 0x707070;
    private static final int WOOL = 0xFFFFFF;
    private static final int SKY = 0xA4C2F4;

    @Test
    void uprightSymbolAlongZShowsItsFaceMirroredAsSeenFromNorth() {
        Map<String, Integer> blocks = Map.of("0,64,5", WOOL, "0,65,5", WOOL, "1,64,5", WOOL);
        Picture picture = Picture.side(world(blocks), new BlockBox(0, 64, 5, 1, 65, 5), View.ALONG_Z, 4);
        assertEquals(SKY, picture.at(1, 0));
        assertEquals(WOOL, picture.at(1, 1));
        assertEquals(WOOL, picture.at(2, 0));
        assertEquals(WOOL, picture.at(2, 1));
        assertEquals(SKY, picture.at(1, 2));
    }

    @Test
    void uprightSymbolAlongXShowsItsFaceAsSeenFromWest() {
        Map<String, Integer> blocks = Map.of("5,64,0", WOOL, "5,65,0", WOOL, "5,64,1", WOOL);
        Picture picture = Picture.side(world(blocks), new BlockBox(5, 64, 0, 5, 65, 1), View.ALONG_X, 4);
        assertEquals(WOOL, picture.at(1, 2));
        assertEquals(WOOL, picture.at(2, 2));
        assertEquals(WOOL, picture.at(2, 3));
        assertEquals(SKY, picture.at(1, 3));
    }

    @Test
    void wallBehindTheSymbolIsDarkened() {
        Map<String, Integer> blocks = Map.of("0,64,5", WOOL, "1,64,9", STONE);
        Picture picture = Picture.side(world(blocks), new BlockBox(0, 64, 5, 1, 65, 5), View.ALONG_Z, 4);
        assertEquals(WOOL, picture.at(2, 1));
        int darkened = (int) (0x70 * 180 / 255.0);
        assertEquals((darkened << 16) | (darkened << 8) | darkened, picture.at(2, 0));
    }

    private static World world(Map<String, Integer> blocks) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMinHeight" -> -64;
                    case "getMaxHeight" -> 320;
                    case "getBlockAt" -> block(blocks.get(args[0] + "," + args[1] + "," + args[2]));
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Block block(Integer rgb) {
        BlockData data = (BlockData) Proxy.newProxyInstance(BlockData.class.getClassLoader(),
                new Class<?>[]{BlockData.class}, (proxy, method, args) -> Color.fromRGB(rgb));
        return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[]{Block.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isEmpty" -> rgb == null;
                    case "getBlockData" -> data;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
