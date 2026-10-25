package com.acomi.acomi_backend.notification.infrastructure.firebase;

import com.acomi.acomi_backend.config.BackendModuleRoot;
import com.acomi.acomi_backend.config.DotEnvLocation;
import com.acomi.acomi_backend.notification.config.PushProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads backend-only Firebase credentials. Never returns or logs the private key.
 * Relative paths are resolved from the loaded {@code .env} directory, then the process directory.
 */
public final class FirebaseCredentialLoader {

    public static final String EXPECTED_PROJECT_ID = "acomi-270d0";
    public static final String RELATIVE_LOCAL_PATH = "secrets/firebase-service-account.json";

    private static final ObjectMapper JSON = new ObjectMapper();

    private FirebaseCredentialLoader() {}

    public record LoadResult(
            String credentialSource,
            boolean credentialReadable,
            String projectStatus,
            String reasonCode,
            GoogleCredentials credentials) {}

    public static LoadResult load(PushProperties properties) {
        if (properties == null || !properties.isEnabled()) {
            LoadResult inspected = inspect(properties);
            return new LoadResult(
                    inspected.credentialSource(),
                    inspected.credentialReadable(),
                    inspected.projectStatus(),
                    "PUSH_DISABLED",
                    null);
        }
        return inspect(properties);
    }

    private static LoadResult inspect(PushProperties properties) {
        if (properties == null) {
            return unavailable("NONE", "CREDENTIAL_MISSING");
        }
        String path = blankToNull(properties.getCredentialsPath());
        if (path != null) {
            return fromFile(path);
        }
        String json = blankToNull(properties.getCredentialsJson());
        if (json != null) {
            return fromBytes(json.getBytes(java.nio.charset.StandardCharsets.UTF_8), "JSON");
        }
        return unavailable("NONE", "CREDENTIAL_MISSING");
    }

    private static LoadResult fromFile(String configured) {
        Path file = resolve(configured);
        if (file == null || !Files.isRegularFile(file) || !Files.isReadable(file)) {
            return unavailable("PATH", "CREDENTIAL_FILE_MISSING");
        }
        try {
            return fromBytes(Files.readAllBytes(file), "PATH");
        } catch (Exception ex) {
            return new LoadResult("PATH", false, "INVALID", "INVALID_CREDENTIAL_JSON", null);
        }
    }

    static Path resolve(String configured) {
        return resolveAgainst(configured, searchRoots());
    }

    static List<Path> searchRoots() {
        List<Path> roots = new ArrayList<>();
        addRoot(roots, DotEnvLocation.directory());
        addRoot(roots, BackendModuleRoot.locate(FirebaseCredentialLoader.class));
        addRoot(roots, Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize());
        return roots;
    }

    /**
     * Relative paths are checked against the loaded {@code .env} directory, then the
     * backend module, then the process directory. The first existing file wins.
     */
    static Path resolveAgainst(String configured, List<Path> roots) {
        Path raw = Path.of(configured.trim());
        if (raw.isAbsolute()) {
            return raw.normalize();
        }
        List<Path> safeRoots = roots == null ? List.of() : roots;
        for (Path root : safeRoots) {
            if (root == null) {
                continue;
            }
            Path candidate = root.resolve(raw).normalize();
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        Path fallback = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        for (Path root : safeRoots) {
            if (root != null) {
                fallback = root;
                break;
            }
        }
        return fallback.resolve(raw).normalize();
    }

    private static void addRoot(List<Path> roots, Path root) {
        if (root == null) {
            return;
        }
        Path normalized = root.toAbsolutePath().normalize();
        if (!roots.contains(normalized)) {
            roots.add(normalized);
        }
    }

    private static LoadResult fromBytes(byte[] bytes, String source) {
        final JsonNode node;
        try {
            node = JSON.readTree(bytes);
        } catch (Exception ex) {
            return new LoadResult(source, false, "INVALID", "INVALID_CREDENTIAL_JSON", null);
        }
        if (node == null || !node.isObject()) {
            return new LoadResult(source, false, "INVALID", "INVALID_CREDENTIAL_JSON", null);
        }
        String type = node.path("type").asText("");
        String projectId = node.path("project_id").asText("");
        String privateKey = node.path("private_key").asText("");
        if (!"service_account".equals(type) || projectId.isBlank() || privateKey.isBlank()) {
            return new LoadResult(source, true, "INVALID", "INVALID_CREDENTIAL_JSON", null);
        }
        if (!EXPECTED_PROJECT_ID.equals(projectId)) {
            return new LoadResult(source, true, "MISMATCH", "PROJECT_MISMATCH", null);
        }
        try {
            GoogleCredentials credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(bytes));
            return new LoadResult(source, true, "MATCH", "READY", credentials);
        } catch (Exception ex) {
            return new LoadResult(source, true, "MATCH", "INVALID_CREDENTIAL_JSON", null);
        }
    }

    private static LoadResult unavailable(String source, String reason) {
        return new LoadResult(source, false, "UNKNOWN", reason, null);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
