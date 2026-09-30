package de.kylekreuter.vistructum.ui.integration.discord;

import org.bukkit.configuration.ConfigurationSection;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public record DiscordSettings(URI webhook, boolean created, boolean reviewed, boolean scanFinished,
                              double minProbability, Optional<String> mentionRole) {

    private static final Set<String> HOSTS = Set.of("discord.com", "discordapp.com", "ptb.discord.com",
            "canary.discord.com");
    private static final Pattern WEBHOOK_PATH = Pattern.compile("/api(/v\\d+)?/webhooks/\\d+/[A-Za-z0-9_-]+/?");
    private static final Pattern SNOWFLAKE = Pattern.compile("\\d{1,20}");

    public DiscordSettings {
        Objects.requireNonNull(webhook, "webhook");
        Objects.requireNonNull(mentionRole, "mentionRole");
    }

    public static Optional<DiscordSettings> from(ConfigurationSection config) {
        if (!config.getBoolean("discord.enabled")) {
            return Optional.empty();
        }
        URI webhook = webhook(config.getString("discord.webhook-url", ""));
        String role = config.getString("discord.mention-role", "").trim();
        if (!role.isEmpty() && !SNOWFLAKE.matcher(role).matches()) {
            throw new IllegalArgumentException("discord.mention-role is not a role ID");
        }
        double minProbability = config.getDouble("discord.min-probability");
        if (minProbability < 0 || minProbability > 1) {
            throw new IllegalArgumentException("discord.min-probability lies outside 0 to 1");
        }
        return Optional.of(new DiscordSettings(webhook, config.getBoolean("discord.events.created"),
                config.getBoolean("discord.events.reviewed"), config.getBoolean("discord.events.scan-finished"),
                minProbability, role.isEmpty() ? Optional.empty() : Optional.of(role)));
    }

    static URI webhook(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("discord.webhook-url is not a URL");
        }
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || !HOSTS.contains(uri.getHost())
                || uri.getRawPath() == null || !WEBHOOK_PATH.matcher(uri.getRawPath()).matches()) {
            throw new IllegalArgumentException("discord.webhook-url is not a Discord webhook URL");
        }
        return uri;
    }

    public boolean announces(double score) {
        return score >= minProbability;
    }
}
