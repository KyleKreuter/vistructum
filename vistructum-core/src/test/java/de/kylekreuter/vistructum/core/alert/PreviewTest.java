package de.kylekreuter.vistructum.core.alert;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PreviewTest {

    @Test
    void maskCropMapsBuiltAndEmptyCells() {
        byte[] modified = {
                0, 0, 0,
                0, 1, 1,
                0, 1, 0};
        Preview preview = Preview.ofMask(modified, 3, 3, 1, 1, 3, 3);
        assertEquals(2, preview.width());
        assertEquals(2, preview.height());
        assertEquals(Preview.BUILT, preview.grey(0, 0));
        assertEquals(Preview.EMPTY, preview.grey(1, 1));
    }

    @Test
    void cropIsClippedToTheScene() {
        Preview preview = Preview.ofLuminance(new byte[]{10, 20, 30, 40}, 2, 2, -5, -5, 10, 10);
        assertEquals(2, preview.width());
        assertEquals(2, preview.height());
        assertEquals(40, preview.grey(1, 1));
    }

    @Test
    void pngIsScaledGreyscale() throws Exception {
        Preview preview = new Preview(2, 1, new byte[]{0, (byte) 200});
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(PreviewImage.png(preview, 3)));
        assertEquals(6, image.getWidth());
        assertEquals(3, image.getHeight());
        assertEquals(0, image.getRaster().getSample(2, 2, 0));
        assertEquals(200, image.getRaster().getSample(3, 0, 0));
    }
}
