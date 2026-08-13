package com.acomi.acomi_backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Flyway loads all {@code db/migration/**} scripts into one version namespace.
 * Duplicate version numbers across modules (meal, member, space, etc.) prevent startup.
 */
class FlywayMigrationVersionTest {

    private static final Pattern VERSION = Pattern.compile("^V(\\d+)__.*\\.sql$");

    @Test
    void migrationVersionsAreGloballyUnique() throws IOException {
        Path root = Path.of("src/main/resources/db/migration");
        Map<String, String> owners = new HashMap<>();

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".sql"))
                    .forEach(path -> {
                        String fileName = path.getFileName().toString();
                        var matcher = VERSION.matcher(fileName);
                        assertThat(matcher.matches())
                                .as("Migration file must be named V<number>__description.sql: %s", path)
                                .isTrue();
                        String version = matcher.group(1);
                        String relative = root.relativize(path).toString().replace('\\', '/');
                        String previous = owners.putIfAbsent(version, relative);
                        assertThat(previous)
                                .as("Duplicate Flyway version %s: %s and %s", version, previous, relative)
                                .isNull();
                    });
        }
    }
}
