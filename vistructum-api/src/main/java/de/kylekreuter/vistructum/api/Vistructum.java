package de.kylekreuter.vistructum.api;

import org.bukkit.Bukkit;

import java.util.concurrent.CompletableFuture;

public interface Vistructum {

    static Vistructum get() {
        Vistructum vistructum = Bukkit.getServicesManager().load(Vistructum.class);
        if (vistructum == null) {
            throw new IllegalStateException("Vistructum is not enabled; add depend: [vistructum] to your plugin.yml");
        }
        return vistructum;
    }

    Findings findings();

    Scans scans();

    CompletableFuture<VistructumStatus> status();
}
