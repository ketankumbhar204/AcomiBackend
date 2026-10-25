package com.acomi.acomi_backend.config;

import java.nio.file.Path;

/** Directory of the `.env` file that was loaded. Not a secret. */
public final class DotEnvLocation {

    private static volatile Path directory;

    private DotEnvLocation() {}

    public static void remember(Path envFile) {
        if (envFile != null && envFile.getParent() != null) {
            directory = envFile.getParent().toAbsolutePath().normalize();
        }
    }

    public static Path directory() {
        return directory;
    }

    public static void clear() {
        directory = null;
    }
}
