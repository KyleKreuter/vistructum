package de.kylekreuter.vistructum.ui.web.view;

import de.kylekreuter.vistructum.api.WebSession;

import java.util.Optional;

public record MeView(String player, String name, String expiresAt, boolean canShare, boolean blockLog,
                     boolean canRollback, String punishments) {

    public static MeView of(WebSession session, boolean canShare, boolean blockLog, boolean canRollback,
                            Optional<String> punishments) {
        return new MeView(session.player().toString(), session.playerName(), session.expiresAt().toString(), canShare,
                blockLog, blockLog && canRollback, punishments.orElse(null));
    }
}
