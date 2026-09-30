package de.kylekreuter.vistructum.ui.integration.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.Review;
import de.kylekreuter.vistructum.api.ScanJob;
import de.kylekreuter.vistructum.api.Verdict;
import de.kylekreuter.vistructum.ui.ScanText;
import de.kylekreuter.vistructum.ui.text.Message;
import de.kylekreuter.vistructum.ui.text.Messages;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static de.kylekreuter.vistructum.ui.text.Messages.number;
import static de.kylekreuter.vistructum.ui.text.Messages.text;

final class DiscordPayloads {

    static final int OPEN_COLOR = 0xFFA000;
    static final int CONFIRMED_COLOR = 0xE53935;
    static final int FALSE_ALARM_COLOR = 0x43A047;
    static final int SCAN_COLOR = 0x1B6E1B;
    private static final int ACTION_ROW = 1;
    private static final int BUTTON = 2;
    private static final int LINK_STYLE = 5;

    private final Messages messages;

    DiscordPayloads(Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    static String imageName(long findingId) {
        return "finding-" + findingId + ".png";
    }

    JsonObject finding(Finding finding, List<String> builders, Optional<String> link, boolean image,
                       Optional<String> mentionRole) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", messages.plain(Message.DISCORD_FINDING, number("id", finding.id())));
        link.ifPresent(url -> embed.addProperty("url", url));
        embed.addProperty("color", color(finding.review()));
        embed.addProperty("timestamp", finding.createdAt().toString());
        JsonArray fields = new JsonArray();
        fields.add(field(Message.DISCORD_FIELD_WORLD, finding.world(), true));
        fields.add(field(Message.DISCORD_FIELD_LOCATION, Messages.location(finding.box()), true));
        fields.add(field(Message.DISCORD_FIELD_PROBABILITY, Messages.probability(finding.score()), true));
        fields.add(field(Message.DISCORD_FIELD_SOURCE, messages.source(finding.source()), true));
        fields.add(field(Message.DISCORD_FIELD_BUILDERS, builders.isEmpty()
                ? messages.plain(Message.DISCORD_BUILDERS_UNKNOWN) : String.join(", ", builders), true));
        fields.add(field(Message.DISCORD_FIELD_VERDICT, verdict(finding.review()), true));
        embed.add("fields", fields);
        if (image) {
            JsonObject picture = new JsonObject();
            picture.addProperty("url", "attachment://" + imageName(finding.id()));
            embed.add("image", picture);
        }
        JsonObject payload = message(embed, mentionRole);
        mentionRole.ifPresent(role -> payload.addProperty("content", "<@&" + role + ">"));
        link.ifPresent(url -> payload.add("components", linkButton(url)));
        return payload;
    }

    JsonObject scanFinished(ScanJob job) {
        JsonObject embed = new JsonObject();
        embed.addProperty("description", messages.plain(switch (job.status()) {
            case DONE, QUEUED, RUNNING -> Message.DISCORD_SCAN_DONE;
            case CANCELLED -> Message.DISCORD_SCAN_STOPPED;
            case FAILED -> Message.DISCORD_SCAN_FAILED;
        }, ScanText.values(job)));
        embed.addProperty("color", SCAN_COLOR);
        return message(embed, Optional.empty());
    }

    private static JsonObject message(JsonObject embed, Optional<String> mentionRole) {
        JsonObject payload = new JsonObject();
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        payload.add("embeds", embeds);
        JsonObject allowedMentions = new JsonObject();
        allowedMentions.add("parse", new JsonArray());
        JsonArray roles = new JsonArray();
        mentionRole.ifPresent(roles::add);
        allowedMentions.add("roles", roles);
        payload.add("allowed_mentions", allowedMentions);
        return payload;
    }

    private JsonArray linkButton(String url) {
        JsonObject button = new JsonObject();
        button.addProperty("type", BUTTON);
        button.addProperty("style", LINK_STYLE);
        button.addProperty("label", messages.plain(Message.DISCORD_OPEN_WEB));
        button.addProperty("url", url);
        JsonArray buttons = new JsonArray();
        buttons.add(button);
        JsonObject row = new JsonObject();
        row.addProperty("type", ACTION_ROW);
        row.add("components", buttons);
        JsonArray rows = new JsonArray();
        rows.add(row);
        return rows;
    }

    private JsonObject field(Message name, String value, boolean inline) {
        JsonObject field = new JsonObject();
        field.addProperty("name", messages.plain(name));
        field.addProperty("value", value);
        field.addProperty("inline", inline);
        return field;
    }

    private String verdict(Optional<Review> review) {
        return review.map(r -> messages.plain(r.verdict() == Verdict.CONFIRMED ? Message.DISCORD_CONFIRMED
                        : Message.DISCORD_FALSE_ALARM, text("reviewer", r.reviewer())))
                .orElseGet(() -> messages.plain(Message.DISCORD_OPEN));
    }

    private static int color(Optional<Review> review) {
        return review.map(r -> r.verdict() == Verdict.CONFIRMED ? CONFIRMED_COLOR : FALSE_ALARM_COLOR)
                .orElse(OPEN_COLOR);
    }
}
