package de.kylekreuter.vistructum.ui.web.view;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public record PlayerView(String uuid, String name) {

    public static List<PlayerView> of(Collection<UUID> players, Map<UUID, Optional<String>> names) {
        return players.stream().sorted(Comparator.comparing(UUID::toString))
                .map(player -> new PlayerView(player.toString(),
                        names.getOrDefault(player, Optional.empty()).orElse(null)))
                .toList();
    }
}
