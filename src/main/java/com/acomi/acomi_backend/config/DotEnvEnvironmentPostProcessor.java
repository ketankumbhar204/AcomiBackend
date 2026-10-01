package com.acomi.acomi_backend.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Loads a local {@code .env} file so storage/R2 settings are available to
 * {@code spring-boot:run} without exporting them by hand.
 */
public class DotEnvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private final Log log;

    public DotEnvEnvironmentPostProcessor() {
        this.log = LogFactory.getLog(DotEnvEnvironmentPostProcessor.class);
    }

    public DotEnvEnvironmentPostProcessor(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(DotEnvEnvironmentPostProcessor.class);
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path file = resolveEnvFile();
        if (file == null) {
            log.info("dotenv_not_found");
            return;
        }
        Map<String, Object> values = readEnvFile(file);
        if (values.isEmpty()) {
            return;
        }
        environment.getPropertySources().addLast(new MapPropertySource("dotenvFile", values));
        log.info("dotenv_loaded path=" + file.toAbsolutePath() + " keys=" + values.size());
    }

    static Path resolveEnvFile() {
        Set<Path> candidates = new LinkedHashSet<>();
        Path userDir = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        candidates.add(Path.of(".env").toAbsolutePath().normalize());
        Path cursor = userDir;
        for (int i = 0; i < 5 && cursor != null; i++) {
            candidates.add(cursor.resolve(".env"));
            candidates.add(cursor.resolve("acomi-backend").resolve(".env"));
            cursor = cursor.getParent();
        }
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private Map<String, Object> readEnvFile(Path file) {
        Map<String, Object> values = new LinkedHashMap<>();
        try {
            for (String raw : Files.readAllLines(file)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("export ")) {
                    line = line.substring(7).trim();
                }
                int split = line.indexOf('=');
                if (split <= 0) {
                    continue;
                }
                String key = line.substring(0, split).trim();
                String value = unquote(line.substring(split + 1).trim());
                if (!key.isEmpty()) {
                    values.put(key, value);
                }
            }
        } catch (IOException ex) {
            log.warn("dotenv_read_failed path=" + file.toAbsolutePath(), ex);
        }
        return values;
    }

    private static String unquote(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
