package de.kylekreuter.vistructum.ui.text;

import de.kylekreuter.vistructum.api.BlockBox;
import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.Source;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class Messages {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Map<Message, String> templates;
    private final TagResolver prefix;
    private final DateTimeFormatter dates;

    private Messages(Map<Message, String> templates, String prefix, DateTimeFormatter dates) {
        this.templates = templates;
        this.prefix = Placeholder.parsed("prefix", prefix);
        this.dates = dates;
    }

    public static Messages from(ConfigurationSection config, ZoneId zone) {
        Map<Message, String> templates = new EnumMap<>(Message.class);
        for (Message message : Message.values()) {
            templates.put(message, required(config, message.path()));
        }
        DateTimeFormatter dates = DateTimeFormatter.ofPattern(required(config, "date-format"), Locale.ENGLISH)
                .withZone(zone);
        return new Messages(templates, required(config, "prefix"), dates);
    }

    public Component chat(Message message, TagResolver... resolvers) {
        return MINI.deserialize(templates.get(message), TagResolver.resolver(prefix, TagResolver.resolver(resolvers)));
    }

    public Component item(Message message, TagResolver... resolvers) {
        return chat(message, resolvers).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public String plain(Message message, TagResolver... resolvers) {
        return PlainTextComponentSerializer.plainText().serialize(chat(message, resolvers));
    }

    public TagResolver finding(Finding finding, Instant now) {
        return TagResolver.resolver(
                number("id", finding.id()),
                text("world", finding.world()),
                text("location", location(finding.box())),
                text("probability", probability(finding.score())),
                text("source", source(finding.source())),
                text("detected", detected(finding.createdAt())),
                text("age", age(Duration.between(finding.createdAt(), now))));
    }

    public String detected(Instant instant) {
        return dates.format(instant);
    }

    public String source(Source source) {
        return plain(switch (source) {
            case MASK -> Message.SOURCE_MASK;
            case FULLSCAN -> Message.SOURCE_FULLSCAN;
        });
    }

    public String age(Duration age) {
        if (age.toMinutes() < 60) {
            return plain(Message.AGE_MINUTES, number("count", Math.max(0, age.toMinutes())));
        }
        if (age.toHours() < 48) {
            return plain(Message.AGE_HOURS, number("count", age.toHours()));
        }
        return plain(Message.AGE_DAYS, number("count", age.toDays()));
    }

    public static String probability(double score) {
        return Math.round(score * 100) + "%";
    }

    public static String location(BlockBox box) {
        return box.centerX() + ", " + Math.floorDiv(box.minY() + box.maxY(), 2) + ", " + box.centerZ();
    }

    public static TagResolver number(String key, long value) {
        return Placeholder.parsed(key, Long.toString(value));
    }

    public static TagResolver text(String key, String value) {
        return Placeholder.unparsed(key, value);
    }

    private static String required(ConfigurationSection config, String path) {
        return Objects.requireNonNull(config.getString(path), () -> "messages.yml lacks " + path);
    }
}
