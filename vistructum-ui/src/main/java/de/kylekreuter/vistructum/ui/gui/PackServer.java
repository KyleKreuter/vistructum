package de.kylekreuter.vistructum.ui.gui;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public final class PackServer implements AutoCloseable {

    private final HttpServer server;

    public PackServer(int port, ResourcePack pack) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/pack.zip", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.sendResponseHeaders(200, pack.zip().length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(pack.zip());
            }
        });
        server.start();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
