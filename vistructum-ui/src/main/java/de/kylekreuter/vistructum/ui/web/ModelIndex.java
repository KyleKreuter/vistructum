package de.kylekreuter.vistructum.ui.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

final class ModelIndex {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final String JSON = ".json";
    private static final String PNG = ".png";
    private static final String MCMETA = ".png.mcmeta";
    private static final List<String> ANIMATION_KEYS = List.of("frametime", "frames", "interpolate");

    private ModelIndex() {
    }

    static void write(Path directory, String version) throws IOException {
        JsonObject index = build(directory, version);
        try (Writer out = new OutputStreamWriter(new GZIPOutputStream(
                Files.newOutputStream(directory.resolve(GameAssets.MODELS))), StandardCharsets.UTF_8)) {
            GSON.toJson(index, out);
        }
    }

    static JsonObject build(Path directory, String version) throws IOException {
        JsonObject blockstates = new JsonObject();
        JsonObject models = new JsonObject();
        JsonObject items = new JsonObject();
        JsonArray textures = new JsonArray();
        JsonObject animated = new JsonObject();
        List<String> files;
        try (Stream<Path> paths = Files.walk(directory)) {
            files = paths.filter(Files::isRegularFile)
                    .map(path -> directory.relativize(path).toString().replace('\\', '/')).sorted().toList();
        }
        for (String file : files) {
            if (file.startsWith("blockstates/") && file.endsWith(JSON)) {
                blockstates.add(strip(file, "blockstates/", JSON), parse(directory, file));
            } else if (file.startsWith("models/") && file.endsWith(JSON)) {
                models.add(strip(file, "models/", JSON), parse(directory, file));
            } else if (file.startsWith("items/") && file.endsWith(JSON)) {
                items.add(strip(file, "items/", JSON), parse(directory, file));
            } else if (file.startsWith(GameAssets.TEXTURES + "/") && file.endsWith(PNG)) {
                textures.add(strip(file, GameAssets.TEXTURES + "/", PNG));
            } else if (file.startsWith(GameAssets.TEXTURES + "/") && file.endsWith(MCMETA)) {
                animation(parse(directory, file)).ifPresent(animation ->
                        animated.add(strip(file, GameAssets.TEXTURES + "/", MCMETA), animation));
            }
        }
        JsonObject index = new JsonObject();
        index.addProperty("version", version);
        index.add("blockstates", blockstates);
        index.add("models", models);
        index.add("items", items);
        index.add("textures", textures);
        index.add("animated", animated);
        return index;
    }

    private static Optional<JsonObject> animation(JsonElement meta) {
        if (!meta.isJsonObject() || !meta.getAsJsonObject().has("animation")
                || !meta.getAsJsonObject().get("animation").isJsonObject()) {
            return Optional.empty();
        }
        JsonObject source = meta.getAsJsonObject().getAsJsonObject("animation");
        JsonObject animation = new JsonObject();
        ANIMATION_KEYS.stream().filter(source::has).forEach(key -> animation.add(key, source.get(key)));
        return Optional.of(animation);
    }

    private static JsonElement parse(Path directory, String file) throws IOException {
        try (Reader reader = Files.newBufferedReader(directory.resolve(file), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        } catch (JsonParseException e) {
            throw new IOException(file + " is not valid JSON", e);
        }
    }

    private static String strip(String file, String prefix, String suffix) {
        return file.substring(prefix.length(), file.length() - suffix.length());
    }
}
