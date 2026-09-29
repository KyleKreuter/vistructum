package de.kylekreuter.vistructum.ui.web.assets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

final class ClientJar {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration METADATA_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration JAR_TIMEOUT = Duration.ofMinutes(2);

    private ClientJar() {
    }

    static Download locate(URI manifest, String version) throws IOException, InterruptedException {
        try (HttpClient client = client()) {
            JsonObject versions = get(client, manifest);
            URI details = null;
            for (JsonElement entry : versions.getAsJsonArray("versions")) {
                JsonObject candidate = entry.getAsJsonObject();
                if (candidate.get("id").getAsString().equals(version)) {
                    details = manifest.resolve(candidate.get("url").getAsString());
                    break;
                }
            }
            if (details == null) {
                throw new IOException("the version manifest does not list " + version);
            }
            JsonObject jar = get(client, details).getAsJsonObject("downloads").getAsJsonObject("client");
            return new Download(details.resolve(jar.get("url").getAsString()),
                    jar.get("sha1").getAsString().toLowerCase(Locale.ROOT));
        } catch (JsonParseException | IllegalStateException | ClassCastException | NullPointerException
                 | UnsupportedOperationException e) {
            throw new IOException("the version metadata of " + version + " cannot be read", e);
        }
    }

    static long download(Download jar, Path target) throws IOException, InterruptedException {
        MessageDigest sha1;
        try {
            sha1 = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        long size;
        try (HttpClient client = client()) {
            HttpResponse<InputStream> response = client.send(request(jar.url(), JAR_TIMEOUT),
                    HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = new DigestInputStream(response.body(), sha1);
                 OutputStream out = Files.newOutputStream(target)) {
                requireOk(response, jar.url());
                size = body.transferTo(out);
            }
        }
        String actual = HexFormat.of().formatHex(sha1.digest());
        if (!actual.equals(jar.sha1())) {
            throw new IOException("the client jar has SHA-1 " + actual + " instead of " + jar.sha1());
        }
        return size;
    }

    private static JsonObject get(HttpClient client, URI uri) throws IOException, InterruptedException {
        HttpResponse<InputStream> response = client.send(request(uri, METADATA_TIMEOUT),
                HttpResponse.BodyHandlers.ofInputStream());
        try (Reader reader = new InputStreamReader(response.body(), StandardCharsets.UTF_8)) {
            requireOk(response, uri);
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static void requireOk(HttpResponse<?> response, URI uri) throws IOException {
        if (response.statusCode() != 200) {
            throw new IOException(uri + " answered " + response.statusCode());
        }
    }

    private static HttpRequest request(URI uri, Duration timeout) {
        return HttpRequest.newBuilder(uri).timeout(timeout).GET().build();
    }

    private static HttpClient client() {
        return HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    record Download(URI url, String sha1) {

        Download {
            Objects.requireNonNull(url, "url");
            Objects.requireNonNull(sha1, "sha1");
        }
    }
}
