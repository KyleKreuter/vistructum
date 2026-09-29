package de.kylekreuter.vistructum.ui.web.controller;

import de.kylekreuter.vistructum.ui.web.WebSettings;
import io.javalin.config.JavalinConfig;

public final class ApiFallback {

    public void register(JavalinConfig config) {
        Routes.notFound(config, WebSettings.API + "*");
    }
}
