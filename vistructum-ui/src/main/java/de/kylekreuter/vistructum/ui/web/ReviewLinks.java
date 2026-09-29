package de.kylekreuter.vistructum.ui.web;

import de.kylekreuter.vistructum.api.WebAccess;
import de.kylekreuter.vistructum.ui.text.Message;
import de.kylekreuter.vistructum.ui.text.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static de.kylekreuter.vistructum.ui.text.Messages.number;

public final class ReviewLinks {

    private final WebAccess web;
    private final WebSettings settings;
    private final Messages messages;

    public ReviewLinks(WebAccess web, WebSettings settings, Messages messages) {
        this.web = Objects.requireNonNull(web, "web");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    public boolean enabled() {
        return settings.enabled();
    }

    public CompletableFuture<Component> home(Player player) {
        return login(player, WebSettings.HOME).thenApply(url -> messages.chat(Message.WEB_LINK,
                Messages.link("link", url), number("minutes", WebAccess.LOGIN_MINUTES)));
    }

    public CompletableFuture<Component> evidence(Player player, long findingId) {
        return login(player, WebSettings.evidencePath(findingId)).thenApply(url -> messages.chat(Message.EVIDENCE_LINK,
                Messages.link("link", url), number("id", findingId), number("minutes", WebAccess.LOGIN_MINUTES)));
    }

    public String shareLink(String shareToken) {
        return settings.shareLink(shareToken);
    }

    private CompletableFuture<String> login(Player player, String next) {
        return web.issueLogin(player.getUniqueId(), player.getName()).thenApply(token -> settings.loginLink(token, next));
    }
}
