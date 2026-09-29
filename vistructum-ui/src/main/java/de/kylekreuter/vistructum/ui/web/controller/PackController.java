package de.kylekreuter.vistructum.ui.web.controller;

import de.kylekreuter.vistructum.ui.gui.ResourcePack;
import de.kylekreuter.vistructum.ui.web.Responses;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;

import java.util.Objects;

public final class PackController {

    private static final String PACK = "/pack.zip";

    private final ResourcePack pack;

    public PackController(ResourcePack pack) {
        this.pack = Objects.requireNonNull(pack, "pack");
    }

    public void register(JavalinConfig config) {
        Routes.any(config, PACK, this::pack);
    }

    private void pack(Context ctx) {
        byte[] zip = pack.zip();
        ctx.status(200).contentType(Responses.ZIP).result(zip);
        ctx.res().setContentLength(zip.length);
    }
}
