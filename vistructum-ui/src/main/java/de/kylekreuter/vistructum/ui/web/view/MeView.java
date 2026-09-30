package de.kylekreuter.vistructum.ui.web.view;

import de.kylekreuter.vistructum.api.WebSession;

public record MeView(String player, String name, String expiresAt, boolean canShare, boolean blockLog,
                     boolean canRollback) {

    public static MeView of(WebSession session, boolean canShare, boolean blockLog, boolean canRollback) {
        return new MeView(session.player().toString(), session.playerName(), session.expiresAt().toString(), canShare,
                blockLog, blockLog && canRollback);
    }
}
