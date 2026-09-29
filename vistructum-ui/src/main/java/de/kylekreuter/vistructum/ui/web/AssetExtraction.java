package de.kylekreuter.vistructum.ui.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class AssetExtraction {

    static final String PREFIX = "assets/minecraft/";

    private static final String NAME = "[a-z0-9_-][a-z0-9_.-]*";
    private static final String NESTED = NAME + "(?:/" + NAME + ")*";
    private static final Pattern WHITELIST = Pattern.compile(Pattern.quote(PREFIX) + "(?:"
            + "blockstates/" + NAME + "\\.json"
            + "|models/(?:block|item)/" + NESTED + "\\.json"
            + "|textures/(?:block|item)/" + NESTED + "\\.png(?:\\.mcmeta)?"
            + "|textures/colormap/(?:grass|foliage)\\.png"
            + "|items/" + NAME + "\\.json)");

    private AssetExtraction() {
    }

    static boolean wanted(String entry) {
        return WHITELIST.matcher(entry).matches();
    }

    static int extract(Path jar, Path target) throws IOException, InterruptedException {
        Path root = target.toAbsolutePath().normalize();
        Files.createDirectories(root);
        int files = 0;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                if (Thread.interrupted()) {
                    throw new InterruptedException("the extraction was interrupted");
                }
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !wanted(entry.getName())) {
                    continue;
                }
                Path file = root.resolve(entry.getName().substring(PREFIX.length())).normalize();
                if (!file.startsWith(root)) {
                    continue;
                }
                Files.createDirectories(file.getParent());
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, file);
                }
                files++;
            }
        }
        return files;
    }
}
