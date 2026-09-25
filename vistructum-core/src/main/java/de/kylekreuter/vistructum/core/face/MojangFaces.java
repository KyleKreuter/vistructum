package de.kylekreuter.vistructum.core.face;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.kylekreuter.vistructum.api.PlayerFace;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;

public final class MojangFaces implements FaceSource {

    private static final int OFFLINE_UUID_VERSION = 3;
    private static final String NAME_LOOKUP = "https://api.minecraftservices.com/minecraft/profile/lookup/name/";
    private static final String SESSION_PROFILE = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public CompletableFuture<PlayerFace> fetch(UUID player, Optional<String> knownName) {
        CompletableFuture<Optional<String>> accountId = player.version() == OFFLINE_UUID_VERSION
                ? knownName.map(this::accountIdByName).orElseGet(MojangFaces::nothing)
                : CompletableFuture.completedFuture(Optional.of(player.toString().replace("-", "")));
        return accountId.thenCompose(id -> id.map(this::sessionProfile).orElseGet(MojangFaces::nothing))
                .thenCompose(session -> {
                    Optional<String> name =
                            knownName.or(() -> session.map(profile -> profile.get("name").getAsString()));
                    return session.flatMap(MojangFaces::skinUrl).map(this::download)
                            .orElseGet(() -> CompletableFuture.completedFuture(List.of()))
                            .thenApply(pixels -> new PlayerFace(player, name, pixels));
                });
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

    static List<Integer> face(BufferedImage skin) {
        List<Integer> pixels = new ArrayList<>(PlayerFace.SIZE * PlayerFace.SIZE);
        for (int row = 0; row < PlayerFace.SIZE; row++) {
            for (int col = 0; col < PlayerFace.SIZE; col++) {
                int base = skin.getRGB(8 + col, 8 + row);
                int hat = skin.getRGB(40 + col, 8 + row);
                pixels.add(((hat >>> 24) > 0 ? hat : base) & 0xFFFFFF);
            }
        }
        return pixels;
    }

    private CompletableFuture<Optional<String>> accountIdByName(String name) {
        return json(URI.create(NAME_LOOKUP + URLEncoder.encode(name, StandardCharsets.UTF_8)))
                .thenApply(account -> account.map(found -> found.get("id").getAsString()));
    }

    private CompletableFuture<Optional<JsonObject>> sessionProfile(String accountId) {
        return json(URI.create(SESSION_PROFILE + accountId));
    }

    private CompletableFuture<Optional<JsonObject>> json(URI uri) {
        return http.sendAsync(request(uri), HttpResponse.BodyHandlers.ofString()).thenApply(response -> switch (
                response.statusCode()) {
            case 200 -> Optional.of(JsonParser.parseString(response.body()).getAsJsonObject());
            case 204, 404 -> Optional.empty();
            default -> throw new IllegalStateException(uri + " answered " + response.statusCode());
        });
    }

    private CompletableFuture<List<Integer>> download(URI skin) {
        return http.sendAsync(request(skin), HttpResponse.BodyHandlers.ofByteArray()).thenApply(response -> {
            if (response.statusCode() != 200) {
                throw new IllegalStateException(skin + " answered " + response.statusCode());
            }
            try {
                return face(ImageIO.read(new ByteArrayInputStream(response.body())));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static <T> CompletableFuture<Optional<T>> nothing() {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    private static HttpRequest request(URI uri) {
        return HttpRequest.newBuilder(uri).timeout(TIMEOUT).build();
    }
}
