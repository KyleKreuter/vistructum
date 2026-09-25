package de.kylekreuter.vistructum.ui.gui;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackTest {

    @Test
    void mapCardsUseTheCardModelsAndKeepTheVanillaMapAsFallback() throws Exception {
        Map<String, String> files = files();
        String items = files.get("assets/minecraft/items/filled_map.json");
        String legacy = files.get("assets/minecraft/models/item/filled_map.json");
        for (String card : new String[]{"card", "card_confirmed", "card_dismissed"}) {
            assertTrue(items.contains("\"vistructum:item/" + card + "\""), card);
            assertTrue(legacy.contains("\"vistructum:item/" + card + "\""), card);
        }
        assertTrue(items.contains("\"model\": \"minecraft:item/filled_map\""));
        assertTrue(items.contains("minecraft:map_color"));
        assertTrue(legacy.contains("minecraft:item/filled_map_markings"));
    }

    private static Map<String, String> files() throws Exception {
        Map<String, String> files = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(ResourcePack.build().zip()))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                files.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }
}
