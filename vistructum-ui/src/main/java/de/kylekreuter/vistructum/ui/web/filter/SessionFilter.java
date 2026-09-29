package de.kylekreuter.vistructum.ui.web.filter;

import de.kylekreuter.vistructum.api.WebAccess;
import de.kylekreuter.vistructum.api.WebSession;
import de.kylekreuter.vistructum.ui.web.Handoff;
import de.kylekreuter.vistructum.ui.web.Requests;
import de.kylekreuter.vistructum.ui.web.error.ApiError;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;

import java.util.Objects;
import java.util.Optional;

public final class SessionFilter {

    private static final String SESSION = SessionFilter.class.getName();

    private final WebAccess web;
    private final Handoff handoff;

    public SessionFilter(WebAccess web, Handoff handoff) {
        this.web = Objects.requireNonNull(web, "web");
        this.handoff = Objects.requireNonNull(handoff, "handoff");
    }

    public void register(JavalinConfig config, String path) {
        config.routes.beforeMatched(path, this::check);
    }

    public static WebSession session(Context ctx) {
        return Objects.requireNonNull(ctx.attribute(SESSION), "the session filter did not run");
    }

    private void check(Context ctx) {
        if (ctx.routeRoles().contains(Access.PUBLIC)) {
            return;
        }
        Optional<String> token = Requests.cookie(ctx, Requests.SESSION_COOKIE);
        if (token.isEmpty()) {
            throw ApiError.unauthorized();
        }
        ctx.future(() -> handoff.off(web.session(token.get())).thenAccept(found ->
                ctx.attribute(SESSION, found.orElseThrow(ApiError::unauthorized))));
    }
}
