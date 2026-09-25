package de.kylekreuter.vistructum.inference;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class GitHubReleases implements ReleaseSource {

    public static final String TAG_PREFIX = "models-";
    public static final String MANIFEST = "models.json";

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final HttpClient client;
    private final URI api;
    private final String repository;
    private final Gson gson = new Gson();

    public GitHubReleases(HttpClient client, URI api, String repository) {
        this.client = client;
        this.api = api;
        this.repository = repository;
    }

    public static GitHubReleases of(String repository) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        return new GitHubReleases(client, URI.create("https://api.github.com/"), repository);
    }

    @Override
    public Optional<ModelRelease> latest() throws IOException {
        JsonArray releases = parse(fetch(api.resolve("repos/" + repository + "/releases?per_page=30"),
                "application/vnd.github+json")).getAsJsonArray();
        for (JsonElement element : releases) {
            JsonObject release = element.getAsJsonObject();
            String tag = release.get("tag_name").getAsString();
            if (release.get("draft").getAsBoolean() || release.get("prerelease").getAsBoolean()
                    || !tag.startsWith(TAG_PREFIX)) {
                continue;
            }
            Map<String, URI> found = new HashMap<>();
            for (JsonElement asset : release.getAsJsonArray("assets")) {
                JsonObject object = asset.getAsJsonObject();
                found.put(object.get("name").getAsString(), URI.create(object.get("browser_download_url").getAsString()));
            }
            URI manifest = found.get(MANIFEST);
            if (manifest == null) {
                continue;
            }
            ModelRelease.Manifest parsed = parseManifest(fetch(manifest, "application/octet-stream"), tag);
            return Optional.of(new ModelRelease(tag, parsed.models(), found));
        }
        return Optional.empty();
    }

    @Override
    public byte[] download(ModelRelease release, ModelRelease.Entry entry) throws IOException {
        URI uri = release.assets().get(entry.file());
        if (uri == null) {
            throw new IOException("release " + release.tag() + " has no asset " + entry.file());
        }
        return fetch(uri, "application/octet-stream");
    }

    private byte[] fetch(URI uri, String accept) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("Accept", accept)
                .header("User-Agent", "vistructum")
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                throw new IOException("GET " + uri + " answered " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("GET " + uri + " interrupted", e);
        }
    }

    private ModelRelease.Manifest parseManifest(byte[] body, String tag) throws IOException {
        try {
            ModelRelease.Manifest manifest = gson.fromJson(new String(body, StandardCharsets.UTF_8),
                    ModelRelease.Manifest.class);
            if (manifest == null || manifest.models() == null) {
                throw new IOException(MANIFEST + " of " + tag + " lists no models");
            }
            return manifest;
        } catch (JsonParseException e) {
            throw new IOException("invalid " + MANIFEST + " in " + tag + ": " + e.getMessage(), e);
        }
    }

    private static JsonElement parse(byte[] body) throws IOException {
        try {
            return JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
        } catch (JsonParseException e) {
            throw new IOException("invalid JSON from GitHub: " + e.getMessage(), e);
        }
    }
}
