package de.kylekreuter.vistructum.core.alert;

import de.kylekreuter.vistructum.api.Preview;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PreviewCropTest {

    @Test
    void maskCropMapsBuiltAndEmptyCells() {
        byte[] modified = {
                0, 0, 0,
                0, 1, 1,
                0, 1, 0};
        Preview preview = PreviewCrop.ofMask(modified, 3, 3, 1, 1, 3, 3);
        assertEquals(2, preview.width());
        assertEquals(2, preview.height());
        assertEquals(PreviewCrop.BUILT, preview.grey(0, 0));
        assertEquals(PreviewCrop.EMPTY, preview.grey(1, 1));
    }

    @Test
    void cropIsClippedToTheScene() {
        Preview preview = PreviewCrop.ofLuminance(new byte[]{10, 20, 30, 40}, 2, 2, -5, -5, 10, 10);
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
