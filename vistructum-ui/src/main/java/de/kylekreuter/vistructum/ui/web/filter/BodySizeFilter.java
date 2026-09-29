package de.kylekreuter.vistructum.ui.web.filter;

import de.kylekreuter.vistructum.ui.web.Requests;
import de.kylekreuter.vistructum.ui.web.error.ApiError;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;

public final class BodySizeFilter {

    public void register(JavalinConfig config, String... paths) {
        for (String path : paths) {
            config.routes.before(path, BodySizeFilter::check);
        }
    }

    private static void check(Context ctx) {
        if (ctx.req().getContentLengthLong() > Requests.MAX_BODY) {
            throw ApiError.badRequest();
        }
    }
}
