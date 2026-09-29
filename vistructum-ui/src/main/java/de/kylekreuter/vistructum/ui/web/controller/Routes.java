package de.kylekreuter.vistructum.ui.web.controller;

import de.kylekreuter.vistructum.ui.web.error.ApiError;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Handler;
import io.javalin.http.HandlerType;
import io.javalin.security.RouteRole;

import java.util.List;

final class Routes {

    private static final List<HandlerType> READING = List.of(HandlerType.GET, HandlerType.HEAD);

    private Routes() {
    }

    static void read(JavalinConfig config, String path, Handler handler, RouteRole... roles) {
        READING.forEach(method -> config.routes.addHttpHandler(method, path, handler, roles));
    }

    static void writing(JavalinConfig config, String path, Handler handler) {
        methods().stream().filter(method -> !READING.contains(method))
                .forEach(method -> config.routes.addHttpHandler(method, path, handler));
    }

    static void any(JavalinConfig config, String path, Handler handler) {
        methods().forEach(method -> config.routes.addHttpHandler(method, path, handler));
    }

    static void notFound(JavalinConfig config, String path) {
        any(config, path, ctx -> {
            throw ApiError.notFound();
        });
    }

    private static List<HandlerType> methods() {
        return HandlerType.values().stream().filter(HandlerType::isHttpMethod).toList();
    }
}
