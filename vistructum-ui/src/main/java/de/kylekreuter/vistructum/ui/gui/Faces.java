package de.kylekreuter.vistructum.ui.gui;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;

final class Faces {

    private static final int OFFLINE_UUID_VERSION = 3;
    private static final String NAME_LOOKUP = "https://api.minecraftservices.com/minecraft/profile/lookup/name/";
    private static final String SESSION_PROFILE = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private Faces() {
    }

    static CompletableFuture<Profile> load(UUID player, Optional<String> knownName) {
        CompletableFuture<Profile> profile = player.version() == OFFLINE_UUID_VERSION && knownName.isPresent()
                ? byName(knownName.get())
                : byId(player, knownName);
        return profile.exceptionally(error -> new Profile(knownName, Optional.empty()));
    }

    static Optional<URI> skinUrl(JsonObject sessionProfile) {
        for (JsonElement property : sessionProfile.getAsJsonArray("properties")) {
            JsonObject entry = property.getAsJsonObject();
            if (entry.get("name").getAsString().equals("textures")) {
                String decoded = new String(Base64.getDecoder().decode(entry.get("value").getAsString()),
                        StandardCharsets.UTF_8);
                JsonObject textures = JsonParser.parseString(decoded).getAsJsonObject().getAsJsonObject("textures");
                return textures.has("SKIN")
                        ? Optional.of(URI.create(textures.getAsJsonObject("SKIN").get("url").getAsString()))
                        : Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static CompletableFuture<Profile> byId(UUID player, Optional<String> knownName) {
        return Bukkit.createProfile(player, knownName.orElse(null)).update()
                .thenCompose(profile -> skin(profile)
                        .thenApply(face -> new Profile(Optional.ofNullable(profile.getName()).or(() -> knownName), face)));
    }

    private static CompletableFuture<Profile> byName(String name) {
        return json(URI.create(NAME_LOOKUP + URLEncoder.encode(name, StandardCharsets.UTF_8)))
                .thenCompose(account -> json(URI.create(SESSION_PROFILE + account.get("id").getAsString())))
                .thenCompose(session -> skinUrl(session).map(Faces::download)
                        .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty())))
                .thenApply(face -> new Profile(Optional.of(name), face));
    }

    private static CompletableFuture<JsonObject> json(URI uri) {
        return HTTP.sendAsync(HttpRequest.newBuilder(uri).timeout(TIMEOUT).build(),
                HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new IllegalStateException(uri + " answered " + response.statusCode());
                    }
                    return JsonParser.parseString(response.body()).getAsJsonObject();
                });
    }

    static Picture placeholder() {
        int[] rgb = new int[64];
        Arrays.fill(rgb, 0x8A8A8A);
        rgb[4 * 8 + 2] = 0x4A4A4A;
        rgb[4 * 8 + 5] = 0x4A4A4A;
        return new Picture(8, 8, rgb);
    }

    private static CompletableFuture<Optional<Picture>> skin(PlayerProfile profile) {
        URL skin = profile.getTextures().getSkin();
        if (skin == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        try {
            return download(skin.toURI());
        } catch (URISyntaxException e) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    private static CompletableFuture<Optional<Picture>> download(URI skin) {
        return HTTP.sendAsync(HttpRequest.newBuilder(skin).timeout(TIMEOUT).build(),
                HttpResponse.BodyHandlers.ofByteArray()).thenApply(response ->
                response.statusCode() == 200 ? Optional.of(face(response.body())) : Optional.empty());
    }

    private static Picture face(byte[] png) {
        BufferedImage skin;
        try {
            skin = ImageIO.read(new ByteArrayInputStream(png));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        int[] rgb = new int[64];
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int base = skin.getRGB(8 + col, 8 + row);
                int hat = skin.getRGB(40 + col, 8 + row);
                rgb[row * 8 + col] = ((hat >>> 24) > 0 ? hat : base) & 0xFFFFFF;
            }
        }
        return new Picture(8, 8, rgb);
    }

    record Profile(Optional<String> name, Optional<Picture> face) {
    }
}
