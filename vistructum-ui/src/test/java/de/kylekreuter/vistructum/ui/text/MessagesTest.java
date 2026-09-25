package de.kylekreuter.vistructum.ui.text;

import de.kylekreuter.vistructum.api.BlockBox;
import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.Review;
import de.kylekreuter.vistructum.api.Source;
import de.kylekreuter.vistructum.api.Verdict;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagesTest {

    private static final Instant CREATED = Instant.parse("2026-09-24T18:05:00Z");
    private static final Finding FINDING = new Finding(42, Source.FULLSCAN, "world", new BlockBox(0, 60, 10, 20, 70, 30),
            0.874, 3, Set.of(), "d4", "bf-scan-2", CREATED, Optional.empty());

    @Test
    void everyMessageHasADefaultAndEveryDefaultIsUsed() throws Exception {
        YamlConfiguration defaults = defaults();
        Set<String> files = defaults.getKeys(true).stream().filter(key -> !defaults.isConfigurationSection(key))
                .collect(Collectors.toSet());
        Set<String> expected = new HashSet<>(Arrays.stream(Message.values()).map(Message::path).toList());
        expected.add("prefix");
        expected.add("date-format");
        assertEquals(expected, files);
    }

    @Test
    void prefixIsAGreenGradient() throws Exception {
        Component line = messages().chat(Message.STATUS_NO_SCAN);
        String plain = PlainTextComponentSerializer.plainText().serialize(line);
        assertTrue(plain.startsWith("[vistructum] "), plain);
        List<Component> letters = new ArrayList<>();
        collect(line, letters);
        int first = letters.stream().filter(c -> c.color() != null).findFirst().orElseThrow().color().value();
        assertTrue(green(first) > red(first) && green(first) > 0xC0, Integer.toHexString(first));
    }

    @Test
    void findingValuesAreFormatted() throws Exception {
        Messages messages = messages();
        String line = messages.plain(Message.REVIEW_LINE, messages.finding(FINDING, CREATED.plus(Duration.ofHours(3))));
        assertEquals("[vistructum] #42 in world at 10, 65, 20, 87%, Fullscan, detected 3 h ago.", line);
        assertEquals("24 Sep 2026, 18:05", messages.detected(CREATED));
    }

    @Test
    void numberPlaceholdersWorkInsideClickCommands() throws Exception {
        Component alert = messages().chat(Message.FINDING_CREATED, messages().finding(FINDING, CREATED));
        List<Component> parts = new ArrayList<>();
        collect(alert, parts);
        assertTrue(parts.stream().map(Component::clickEvent).anyMatch(
                click -> click != null && click.action() == ClickEvent.Action.RUN_COMMAND
                        && click.value().equals("/vis show 42")));
    }

    @Test
    void namesAreNotParsedAsTags() throws Exception {
        Finding reviewed = new Finding(7, Source.MASK, "world", FINDING.box(), 0.5, 1, Set.of(), "", "bf-mask-1",
                CREATED, Optional.of(new Review(Verdict.CONFIRMED, "<red>Staff", CREATED)));
        String line = messages().plain(Message.FINDING_CONFIRMED, Messages.number("id", reviewed.id()),
                Messages.text("reviewer", reviewed.review().orElseThrow().reviewer()));
        assertEquals("[vistructum] Finding #7 was confirmed by <red>Staff.", line);
    }

    private static Messages messages() throws Exception {
        return Messages.from(defaults(), ZoneOffset.UTC);
    }

    private static YamlConfiguration defaults() throws Exception {
        try (Reader reader = new InputStreamReader(MessagesTest.class.getResourceAsStream("/messages.yml"),
                StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }

    private static void collect(Component component, List<Component> into) {
        into.add(component);
        component.children().forEach(child -> collect(child, into));
    }

    private static int green(int rgb) {
        return (rgb >> 8) & 0xFF;
    }

    private static int red(int rgb) {
        return (rgb >> 16) & 0xFF;
    }
}
