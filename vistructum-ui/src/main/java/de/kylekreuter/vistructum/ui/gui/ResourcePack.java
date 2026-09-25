package de.kylekreuter.vistructum.ui.gui;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public record ResourcePack(byte[] zip, String sha1) {

    static final List<String> ICONS = List.of("confirm", "deny", "teleport", "list", "card", "previous", "next", "previous_disabled",
            "next_disabled", "filter_open", "filter_closed", "card_confirmed", "card_dismissed");
    static final int FIRST_MODEL_DATA = 7001;

    public static ResourcePack build() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        text(files, "pack.mcmeta", """
                {"pack": {"pack_format": 34, "supported_formats": {"min_inclusive": 34, "max_inclusive": 999},
                  "min_format": 34, "max_format": 999, "description": "vistructum review menu"}}
                """);
        files.put("assets/vistructum/textures/font/pixel_tall.png", Textures.pixel(Glyphs.PIXEL_TEXTURE_HEIGHT));
        files.put("assets/vistructum/textures/font/detail.png", Textures.detailBackground());
        files.put("assets/vistructum/textures/font/scene.png", Textures.scenePanel());
        files.put("assets/vistructum/textures/font/info.png", Textures.infoPanel());
        files.put("assets/vistructum/textures/font/list.png", Textures.listBackground());
        files.put("assets/vistructum/textures/font/logo.png", resource("logo.png"));
        text(files, "assets/vistructum/font/gui.json", fontJson());
        for (int top = Glyphs.FIRST_LINE; top < Layout.DETAIL_HEIGHT; top++) {
            text(files, "assets/vistructum/font/line_" + top + ".json", lineFontJson(top));
        }
        List<String> overrides = new ArrayList<>();
        List<String> entries = new ArrayList<>();
        for (int index = 0; index < ICONS.size(); index++) {
            String icon = ICONS.get(index);
            int modelData = FIRST_MODEL_DATA + index;
            files.put("assets/vistructum/textures/item/" + icon + ".png", Textures.icon(icon));
            text(files, "assets/vistructum/models/item/" + icon + ".json",
                    "{\"parent\": \"minecraft:item/generated\", \"textures\": {\"layer0\": \"vistructum:item/" + icon + "\"}}");
            overrides.add("{\"predicate\": {\"custom_model_data\": " + modelData + "}, \"model\": \"vistructum:item/" + icon
                    + "\"}");
            entries.add("{\"threshold\": " + modelData + ", \"model\": {\"type\": \"minecraft:model\", \"model\": "
                    + "\"vistructum:item/" + icon + "\"}}");
        }
        text(files, "assets/minecraft/models/item/paper.json", "{\"parent\": \"minecraft:item/generated\", "
                + "\"textures\": {\"layer0\": \"minecraft:item/paper\"}, \"overrides\": [" + String.join(", ", overrides)
                + "]}");
        text(files, "assets/minecraft/items/paper.json", "{\"model\": {\"type\": \"minecraft:range_dispatch\", "
                + "\"property\": \"minecraft:custom_model_data\", \"index\": 0, \"entries\": [" + String.join(", ", entries)
                + "], \"fallback\": {\"type\": \"minecraft:model\", \"model\": \"minecraft:item/paper\"}}}");
        byte[] zip = zip(files);
        return new ResourcePack(zip, sha1(zip));
    }

    private static String fontJson() {
        List<String> providers = new ArrayList<>();
        Map<String, Integer> shifts = new LinkedHashMap<>();
        for (int step = 0; step < Glyphs.SHIFT_STEPS; step++) {
            shifts.put(escape(Glyphs.shiftChar(-(1 << step))), -(1 << step));
            shifts.put(escape(Glyphs.shiftChar(1 << step)), 1 << step);
        }
        StringBuilder advances = new StringBuilder();
        shifts.forEach((glyph, advance) -> advances.append(advances.isEmpty() ? "" : ", ")
                .append('"').append(glyph).append("\": ").append(advance));
        providers.add("{\"type\": \"space\", \"advances\": {" + advances + "}}");
        providers.add(bitmap("vistructum:font/list.png", Layout.LIST_HEIGHT, Glyphs.BACKGROUND_ASCENT,
                Glyphs.LIST_BACKGROUND));
        providers.add(bitmap("vistructum:font/detail.png", Layout.DETAIL_HEIGHT, Glyphs.BACKGROUND_ASCENT,
                Glyphs.DETAIL_BACKGROUND));
        providers.add(bitmap("vistructum:font/scene.png", Layout.DETAIL_HEIGHT, Glyphs.BACKGROUND_ASCENT,
                Glyphs.SCENE_PANEL));
        providers.add(bitmap("vistructum:font/info.png", Layout.DETAIL_HEIGHT, Glyphs.BACKGROUND_ASCENT,
                Glyphs.INFO_PANEL));
        providers.add(bitmap("vistructum:font/logo.png", Layout.LOGO_HEIGHT, Glyphs.ascentAt(Layout.LOGO_TOP),
                Glyphs.LOGO));
        for (int pixelSize : Glyphs.PIXEL_SIZES) {
            for (int top = 0; top < Layout.DETAIL_HEIGHT; top++) {
                providers.add(bitmap("vistructum:font/pixel_tall.png", Glyphs.PIXEL_TEXTURE_HEIGHT * pixelSize,
                        Glyphs.ascentAt(top), Glyphs.pixel(pixelSize, top)));
            }
        }
        return "{\"providers\": [\n" + String.join(",\n", providers) + "\n]}";
    }

    private static byte[] resource(String name) {
        try (InputStream in = ResourcePack.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException(name + " is missing from the plugin jar");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String lineFontJson(int top) {
        List<String> rows = new ArrayList<>();
        for (int row = 0; row < 16; row++) {
            StringBuilder chars = new StringBuilder();
            for (int col = 0; col < 16; col++) {
                int code = row * 16 + col;
                chars.append(escape(code > ' ' && code < 0x7F ? (char) code : '\u0000'));
            }
            rows.add("\"" + chars + "\"");
        }
        return "{\"providers\": [{\"type\": \"space\", \"advances\": {\" \": 4}}, {\"type\": \"bitmap\", "
                + "\"file\": \"minecraft:font/ascii.png\", \"height\": 8, \"ascent\": " + Glyphs.ascentAt(top)
                + ", \"chars\": [" + String.join(", ", rows) + "]}]}";
    }

    private static String bitmap(String file, int height, int ascent, char glyph) {
        return "{\"type\": \"bitmap\", \"file\": \"" + file + "\", \"height\": " + height + ", \"ascent\": " + ascent
                + ", \"chars\": [\"" + escape(glyph) + "\"]}";
    }

    private static String escape(char glyph) {
        return String.format("\\u%04X", (int) glyph);
    }

    private static void text(Map<String, byte[]> files, String path, String content) {
        files.put(path, content.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] zip(Map<String, byte[]> files) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                ZipEntry entry = new ZipEntry(file.getKey());
                entry.setTime(0);
                out.putNextEntry(entry);
                out.write(file.getValue());
                out.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
