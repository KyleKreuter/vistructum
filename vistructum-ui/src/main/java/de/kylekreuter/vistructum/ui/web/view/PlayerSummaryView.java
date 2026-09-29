package de.kylekreuter.vistructum.ui.web.view;

public record PlayerSummaryView(String uuid, String name, CountsView findings) {

    public static PlayerSummaryView of(PlayerView player, CountsView findings) {
        return new PlayerSummaryView(player.uuid(), player.name(), findings);
    }

    public record CountsView(long total, long open, long confirmed, long falseAlarms) {
    }
}
