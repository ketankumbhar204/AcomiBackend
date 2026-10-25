package com.acomi.acomi_backend.config;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Locates the backend module that contains {@code .env} and local secrets.
 * Uses the compiled class location so a launch from another working directory
 * still finds this module.
 */
public final class BackendModuleRoot {

    private BackendModuleRoot() {}

    public static Path locate(Class<?> type) {
        if (type == null) {
            return null;
        }
        try {
            var source = type.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                return null;
            }
            return fromCodeSource(Path.of(source.getLocation().toURI()));
        } catch (URISyntaxException | IllegalArgumentException ex) {
            return null;
        }
    }

    static Path fromCodeSource(Path location) {
        if (location == null) {
            return null;
        }
        Path path = location.toAbsolutePath().normalize();
        if (Files.isRegularFile(path)) {
            Path parent = path.getParent();
            if (parent == null) {
                return null;
            }
            path = parent;
        }
        Path cursor = path;
        for (int i = 0; i < 8 && cursor != null; i++) {
            if (Files.isRegularFile(cursor.resolve("pom.xml"))
                    && Files.isDirectory(cursor.resolve("src").resolve("main"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        return null;
    }
}
