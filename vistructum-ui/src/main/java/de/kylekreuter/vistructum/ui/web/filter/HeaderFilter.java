package de.kylekreuter.vistructum.ui.web.filter;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;

import java.util.List;

public final class HeaderFilter {

    private static final List<String> COMPRESSIBLE = List.of("application/json", "text/", "application/javascript",
            "image/svg");

    public void register(JavalinConfig config, String... paths) {
        for (String path : paths) {
            config.routes.after(path, HeaderFilter::apply);
        }
    }

    private static void apply(Context ctx) {
        ctx.header("X-Content-Type-Options", "nosniff");
        String type = ctx.res().getContentType();
        if (type != null && COMPRESSIBLE.stream().anyMatch(type::startsWith)) {
            ctx.res().addHeader("Vary", "Accept-Encoding");
        }
    }
}
