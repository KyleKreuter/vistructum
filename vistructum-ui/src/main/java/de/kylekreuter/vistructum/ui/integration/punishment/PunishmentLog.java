package de.kylekreuter.vistructum.ui.integration.punishment;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PunishmentLog {

    int LIMIT = 100;

    String source();

    CompletableFuture<List<Punishment>> history(UUID player);
}
