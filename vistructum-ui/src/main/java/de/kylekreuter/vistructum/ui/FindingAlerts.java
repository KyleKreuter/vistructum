package de.kylekreuter.vistructum.ui;

import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.FindingCreatedEvent;
import de.kylekreuter.vistructum.api.FindingReviewedEvent;
import de.kylekreuter.vistructum.api.Review;
import de.kylekreuter.vistructum.api.ScanFinishedEvent;
import de.kylekreuter.vistructum.api.Verdict;
import de.kylekreuter.vistructum.ui.text.Message;
import de.kylekreuter.vistructum.ui.text.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.time.Clock;
import java.util.Objects;

public final class FindingAlerts implements Listener {

    private final String staffPermission;
    private final Messages messages;
    private final Clock clock;

    public FindingAlerts(String staffPermission, Messages messages, Clock clock) {
        this.staffPermission = Objects.requireNonNull(staffPermission, "staffPermission");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCreated(FindingCreatedEvent event) {
        broadcast(messages.chat(Message.FINDING_CREATED, messages.finding(event.getFinding(), clock.instant())));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onReviewed(FindingReviewedEvent event) {
        Finding finding = event.getFinding();
        finding.review().ifPresent(review -> broadcast(verdict(messages, finding, review)));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onScanFinished(ScanFinishedEvent event) {
        broadcast(ScanText.finished(messages, event.getJob()));
    }

    public static Component verdict(Messages messages, Finding finding, Review review) {
        return messages.chat(review.verdict() == Verdict.CONFIRMED ? Message.FINDING_CONFIRMED : Message.FINDING_FALSE_ALARM,
                Messages.number("id", finding.id()), Messages.text("reviewer", review.reviewer()));
    }

    private void broadcast(Component message) {
        Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.hasPermission(staffPermission))
                .forEach(player -> player.sendMessage(message));
    }
}
