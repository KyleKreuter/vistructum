package de.kylekreuter.vistructum.core.face;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MojangFacesTest {

    @Test
    void skinUrlIsReadFromTheTexturesProperty() {
        String textures = "{\"textures\": {\"SKIN\": {\"url\": \"http://textures.minecraft.net/texture/abc\"}}}";
        assertEquals(Optional.of(URI.create("http://textures.minecraft.net/texture/abc")),
                MojangFaces.skinUrl(JsonParser.parseString(session(textures)).getAsJsonObject()));
    }

    @Test
    void profileWithoutSkinHasNoSkinUrl() {
        assertEquals(Optional.empty(), MojangFaces.skinUrl(JsonParser.parseString(session("{\"textures\": {}}"))
                .getAsJsonObject()));
        assertEquals(Optional.empty(), MojangFaces.skinUrl(JsonParser.parseString("{\"properties\": []}")
                .getAsJsonObject()));
    }

    @Test
    void opaqueHatReplacesTheBaseLayer() {
        BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        skin.setRGB(8, 8, 0xFF112233);
        skin.setRGB(9, 8, 0xFF445566);
        skin.setRGB(41, 8, 0xFFAABBCC);
        assertEquals(0x112233, MojangFaces.face(skin).get(0));
        assertEquals(0xAABBCC, MojangFaces.face(skin).get(1));
        assertEquals(64, MojangFaces.face(skin).size());
    }

    private static String session(String textures) {
        String value = Base64.getEncoder().encodeToString(textures.getBytes(StandardCharsets.UTF_8));
        return "{\"id\": \"00\", \"properties\": [{\"name\": \"textures\", \"value\": \"" + value + "\"}]}";
    }
}
