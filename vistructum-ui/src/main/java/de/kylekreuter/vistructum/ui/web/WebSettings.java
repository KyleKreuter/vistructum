package de.kylekreuter.vistructum.ui.web;

import org.bukkit.configuration.ConfigurationSection;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record WebSettings(boolean enabled, String bindAddress, String publicUrl) {

    public static final String ROOT = "/review";
    public static final String HOME = ROOT + "/";

    public WebSettings {
        Objects.requireNonNull(bindAddress, "bindAddress");
        Objects.requireNonNull(publicUrl, "publicUrl");
        publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }

    public static WebSettings from(ConfigurationSection config) {
        return new WebSettings(config.getBoolean("web.enabled"),
                Objects.requireNonNull(config.getString("web.bind-address"), "web.bind-address"),
                Objects.requireNonNull(config.getString("web.public-url"), "web.public-url"));
    }

    public boolean secure() {
        return publicUrl.startsWith("https");
    }

    public String loginLink(String loginToken, String next) {
        return publicUrl + ROOT + "/login?token=" + encode(loginToken) + "&next=" + encode(next);
    }

    public String shareLink(String shareToken) {
        return publicUrl + ROOT + "/e/" + encode(shareToken);
    }

    public static String evidencePath(long findingId) {
        return ROOT + "/findings/" + findingId + "/evidence";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
