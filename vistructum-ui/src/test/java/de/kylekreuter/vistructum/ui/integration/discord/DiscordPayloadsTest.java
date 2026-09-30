package de.kylekreuter.vistructum.ui.integration.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.kylekreuter.vistructum.api.BlockBox;
import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.Review;
import de.kylekreuter.vistructum.api.ScanCause;
import de.kylekreuter.vistructum.api.ScanJob;
import de.kylekreuter.vistructum.api.ScanStatus;
import de.kylekreuter.vistructum.api.Source;
import de.kylekreuter.vistructum.api.Verdict;
import de.kylekreuter.vistructum.ui.text.Messages;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordPayloadsTest {

    private static final Instant CREATED = Instant.parse("2026-09-30T10:00:00Z");
    private static final Finding FINDING = new Finding(7, Source.FULLSCAN, "world", new BlockBox(0, 60, 10, 20, 70, 30),
            0.874, 3, Set.of(), "d4", "bf-scan-3", CREATED, Optional.empty());

    @Test
    void newFindingIsAnOpenEmbedWithoutPing() throws Exception {
        JsonObject payload = payloads().finding(FINDING, List.of("Alex", "Steve"), Optional.empty(), false,
                Optional.empty());
        JsonObject embed = payload.getAsJsonArray("embeds").get(0).getAsJsonObject();

        assertEquals("Finding #7", embed.get("title").getAsString());
        assertEquals(DiscordPayloads.OPEN_COLOR, embed.get("color").getAsInt());
        assertEquals(CREATED.toString(), embed.get("timestamp").getAsString());
        assertEquals("10, 65, 20", field(embed, "Location"));
        assertEquals("87%", field(embed, "Probability"));
        assertEquals("Alex, Steve", field(embed, "Builders"));
        assertEquals("Not reviewed yet", field(embed, "Verdict"));
        assertFalse(embed.has("image"));
        assertFalse(embed.has("url"));
        assertFalse(payload.has("content"));
        assertFalse(payload.has("components"));
        assertTrue(payload.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").isEmpty());
        assertTrue(payload.getAsJsonObject("allowed_mentions").getAsJsonArray("roles").isEmpty());
    }

    @Test
    void linkImageAndRoleAreAdded() throws Exception {
        JsonObject payload = payloads().finding(FINDING, List.of(), Optional.of("https://mc.example/review/findings/7"),
                true, Optional.of("42"));
        JsonObject embed = payload.getAsJsonArray("embeds").get(0).getAsJsonObject();

        assertEquals("https://mc.example/review/findings/7", embed.get("url").getAsString());
        assertEquals("attachment://finding-7.png", embed.getAsJsonObject("image").get("url").getAsString());
        assertEquals("Unknown", field(embed, "Builders"));
        assertEquals("<@&42>", payload.get("content").getAsString());
        assertEquals("42", payload.getAsJsonObject("allowed_mentions").getAsJsonArray("roles").get(0).getAsString());
        JsonObject button = payload.getAsJsonArray("components").get(0).getAsJsonObject()
                .getAsJsonArray("components").get(0).getAsJsonObject();
        assertEquals(5, button.get("style").getAsInt());
        assertEquals("Open web", button.get("label").getAsString());
        assertEquals("https://mc.example/review/findings/7", button.get("url").getAsString());
    }

    @Test
    void verdictChangesColorAndField() throws Exception {
        Finding confirmed = withReview(Verdict.CONFIRMED);
        Finding falseAlarm = withReview(Verdict.FALSE_ALARM);

        JsonObject confirmedEmbed = embed(confirmed);
        JsonObject falseAlarmEmbed = embed(falseAlarm);

        assertEquals(DiscordPayloads.CONFIRMED_COLOR, confirmedEmbed.get("color").getAsInt());
        assertEquals("Confirmed by kyleonaut", field(confirmedEmbed, "Verdict"));
        assertEquals(DiscordPayloads.FALSE_ALARM_COLOR, falseAlarmEmbed.get("color").getAsInt());
        assertEquals("False alarm by kyleonaut", field(falseAlarmEmbed, "Verdict"));
    }

    @Test
    void finishedScanIsDescribed() throws Exception {
        ScanJob job = new ScanJob(3, "world_nether", ScanCause.MANUAL, ScanStatus.DONE, 40, 40, 2, 0, CREATED);
        JsonObject embed = payloads().scanFinished(job).getAsJsonArray("embeds").get(0).getAsJsonObject();
        assertEquals("Scan #3 of world_nether finished with 2 findings.", embed.get("description").getAsString());
    }

    private static Finding withReview(Verdict verdict) {
        return new Finding(FINDING.id(), FINDING.source(), FINDING.world(), FINDING.box(), FINDING.score(),
                FINDING.votes(), FINDING.players(), FINDING.detail(), FINDING.modelVersion(), FINDING.createdAt(),
                Optional.of(new Review(verdict, "kyleonaut", CREATED)));
    }

    private static JsonObject embed(Finding finding) throws Exception {
        return payloads().finding(finding, List.of(), Optional.empty(), false, Optional.empty())
                .getAsJsonArray("embeds").get(0).getAsJsonObject();
    }

    private static String field(JsonObject embed, String name) {
        JsonArray fields = embed.getAsJsonArray("fields");
        for (int i = 0; i < fields.size(); i++) {
            JsonObject field = fields.get(i).getAsJsonObject();
            if (field.get("name").getAsString().equals(name)) {
                return field.get("value").getAsString();
            }
        }
        throw new AssertionError("no field " + name);
    }

    private static DiscordPayloads payloads() throws Exception {
        try (Reader reader = new InputStreamReader(Objects.requireNonNull(
                DiscordPayloadsTest.class.getResourceAsStream("/messages.yml")), StandardCharsets.UTF_8)) {
            return new DiscordPayloads(Messages.from(YamlConfiguration.loadConfiguration(reader), ZoneOffset.UTC));
        }
    }
}
