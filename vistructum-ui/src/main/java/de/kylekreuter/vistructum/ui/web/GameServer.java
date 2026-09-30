package de.kylekreuter.vistructum.ui.web;

import java.util.Optional;
import java.util.UUID;

public interface GameServer {

    Optional<String> playerName(UUID player);

    boolean canShare(UUID player);

    boolean canRollback(UUID player);

    Optional<String> dimension(String world);
}
