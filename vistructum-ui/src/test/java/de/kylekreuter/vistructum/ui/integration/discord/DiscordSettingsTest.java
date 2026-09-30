package de.kylekreuter.vistructum.ui.integration.discord;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordSettingsTest {

    private static final String WEBHOOK = "https://discord.com/api/webhooks/123456/abc_DEF-9";

    private static YamlConfiguration config(boolean enabled, String url, String role, double minProbability) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("discord.enabled", enabled);
        config.set("discord.webhook-url", url);
        config.set("discord.events.created", true);
        config.set("discord.events.reviewed", false);
        config.set("discord.events.scan-finished", true);
        config.set("discord.min-probability", minProbability);
        config.set("discord.mention-role", role);
        return config;
    }

    @Test
    void disabledIntegrationHasNoSettings() {
        assertTrue(DiscordSettings.from(config(false, "", "", 0)).isEmpty());
    }

    @Test
    void enabledIntegrationReadsAllValues() {
        DiscordSettings settings = DiscordSettings.from(config(true, WEBHOOK, "987", 0.9)).orElseThrow();
        assertEquals(URI.create(WEBHOOK), settings.webhook());
        assertTrue(settings.created());
        assertFalse(settings.reviewed());
        assertTrue(settings.scanFinished());
        assertEquals(Optional.of("987"), settings.mentionRole());
        assertTrue(settings.announces(0.9));
        assertFalse(settings.announces(0.89));
    }

    @Test
    void emptyRoleMentionsNobody() {
        assertTrue(DiscordSettings.from(config(true, WEBHOOK, " ", 0)).orElseThrow().mentionRole().isEmpty());
    }

    @Test
    void onlyDiscordWebhookUrlsAreAccepted() {
        assertTrue(DiscordSettings.from(config(true, "https://discordapp.com/api/webhooks/1/x", "", 0)).isPresent());
        assertTrue(DiscordSettings.from(config(true, "https://canary.discord.com/api/v10/webhooks/1/x?thread_id=2", "", 0))
                .isPresent());
        for (String url : new String[]{"", "http://discord.com/api/webhooks/1/x", "https://example.com/api/webhooks/1/x",
                "https://discord.com/api/channels/1", "not a url"}) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> DiscordSettings.from(config(true, url, "", 0)));
            assertFalse(error.getMessage().contains("example.com"));
        }
    }

    @Test
    void invalidRoleAndProbabilityAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> DiscordSettings.from(config(true, WEBHOOK, "@admins", 0)));
        assertThrows(IllegalArgumentException.class, () -> DiscordSettings.from(config(true, WEBHOOK, "", 1.5)));
    }
}
