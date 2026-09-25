package de.kylekreuter.vistructum.ui.gui;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Bukkit;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;

final class Faces {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private Faces() {
    }

    static CompletableFuture<Profile> load(UUID player, Optional<String> knownName) {
        return Bukkit.createProfile(player, knownName.orElse(null)).update()
                .thenCompose(profile -> skin(profile)
                        .thenApply(face -> new Profile(Optional.ofNullable(profile.getName()).or(() -> knownName), face)))
                .exceptionally(error -> new Profile(knownName, Optional.empty()));
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
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(skin.toURI()).timeout(TIMEOUT).build();
        } catch (URISyntaxException e) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenApply(response ->
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
