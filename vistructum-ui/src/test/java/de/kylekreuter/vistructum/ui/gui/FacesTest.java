package de.kylekreuter.vistructum.ui.gui;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FacesTest {

    @Test
    void skinUrlIsReadFromTheTexturesProperty() {
        String textures = "{\"textures\": {\"SKIN\": {\"url\": \"http://textures.minecraft.net/texture/abc\"}}}";
        assertEquals(Optional.of(URI.create("http://textures.minecraft.net/texture/abc")),
                Faces.skinUrl(JsonParser.parseString(session(textures)).getAsJsonObject()));
    }

    @Test
    void profileWithoutSkinHasNoSkinUrl() {
        assertEquals(Optional.empty(), Faces.skinUrl(JsonParser.parseString(session("{\"textures\": {}}"))
                .getAsJsonObject()));
        assertEquals(Optional.empty(), Faces.skinUrl(JsonParser.parseString("{\"properties\": []}")
                .getAsJsonObject()));
    }

    private static String session(String textures) {
        String value = Base64.getEncoder().encodeToString(textures.getBytes(StandardCharsets.UTF_8));
        return "{\"id\": \"00\", \"properties\": [{\"name\": \"textures\", \"value\": \"" + value + "\"}]}";
    }
}
