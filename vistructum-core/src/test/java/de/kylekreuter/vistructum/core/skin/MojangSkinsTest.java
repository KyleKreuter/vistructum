package de.kylekreuter.vistructum.core.skin;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MojangSkinsTest {

    @Test
    void skinTextureIsReadFromTheTexturesProperty() {
        String textures = "{\"textures\": {\"SKIN\": {\"url\": \"http://textures.minecraft.net/texture/abc\"}}}";
        assertEquals(Optional.of(new MojangSkins.SkinTexture(URI.create("http://textures.minecraft.net/texture/abc"),
                false)), MojangSkins.skinTexture(JsonParser.parseString(session(textures)).getAsJsonObject()));
    }

    @Test
    void slimModelIsReadFromTheMetadata() {
        String textures = "{\"textures\": {\"SKIN\": {\"url\": \"http://textures.minecraft.net/texture/abc\","
                + " \"metadata\": {\"model\": \"slim\"}}}}";
        assertTrue(MojangSkins.skinTexture(JsonParser.parseString(session(textures)).getAsJsonObject()).orElseThrow()
                .slim());
    }

    @Test
    void profileWithoutSkinHasNoSkinTexture() {
        assertEquals(Optional.empty(), MojangSkins.skinTexture(JsonParser.parseString(session("{\"textures\": {}}"))
                .getAsJsonObject()));
        assertEquals(Optional.empty(), MojangSkins.skinTexture(JsonParser.parseString("{\"properties\": []}")
                .getAsJsonObject()));
    }

    private static String session(String textures) {
        String value = Base64.getEncoder().encodeToString(textures.getBytes(StandardCharsets.UTF_8));
        return "{\"id\": \"00\", \"properties\": [{\"name\": \"textures\", \"value\": \"" + value + "\"}]}";
    }
}
