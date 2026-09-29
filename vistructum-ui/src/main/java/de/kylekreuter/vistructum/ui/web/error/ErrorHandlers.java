package de.kylekreuter.vistructum.ui.web.error;

import de.kylekreuter.vistructum.ui.web.Responses;
import io.javalin.config.JavalinConfig;
import io.javalin.http.HttpResponseException;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ErrorHandlers {

    private final Logger logger;

    public ErrorHandlers(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void register(JavalinConfig config) {
        config.routes.exception(ApiError.class, (error, ctx) -> Responses.error(ctx, error.status(), error.code()));
        config.routes.exception(HttpResponseException.class, (error, ctx) -> {
            if (error.getStatus() == 404) {
                Responses.notFound(ctx);
            } else {
                Responses.text(ctx, error.getStatus(), error.getMessage());
            }
        });
        config.routes.exception(Exception.class, (error, ctx) -> {
            logger.log(Level.WARNING, ctx.method() + " " + ctx.path() + " failed", error);
            Responses.error(ctx, 500, "internal");
        });
    }
}
