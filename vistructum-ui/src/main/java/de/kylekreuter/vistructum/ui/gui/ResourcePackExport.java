package de.kylekreuter.vistructum.ui.gui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ResourcePackExport {

    private ResourcePackExport() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: ResourcePackExport <target zip>");
        }
        Path target = Path.of(args[0]);
        Files.createDirectories(target.toAbsolutePath().getParent());
        Files.write(target, ResourcePack.build().zip());
    }
}
