package com.acomi.acomi_backend.notification.infrastructure.firebase;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.config.DotEnvLocation;
import com.acomi.acomi_backend.notification.config.PushProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FirebaseCredentialLoaderTest {

    private final ObjectMapper json = new ObjectMapper();

    @AfterEach
    void clearDotenv() {
        DotEnvLocation.clear();
    }

    @Test
    void missingCredentialFileIsReportedWithoutAPath() {
        PushProperties properties = enabled("secrets/does-not-exist.json");
        DotEnvLocation.remember(Path.of(".").toAbsolutePath().resolve(".env"));

        FirebaseCredentialLoader.LoadResult result = FirebaseCredentialLoader.load(properties);

        assertThat(result.credentialSource()).isEqualTo("PATH");
        assertThat(result.credentialReadable()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("CREDENTIAL_FILE_MISSING");
        assertThat(result.credentials()).isNull();
    }

    @Test
    void invalidJsonIsRejected(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("bad.json");
        Files.writeString(file, "{not-json");
        PushProperties properties = enabled(file.toString());

        FirebaseCredentialLoader.LoadResult result = FirebaseCredentialLoader.load(properties);

        assertThat(result.reasonCode()).isEqualTo("INVALID_CREDENTIAL_JSON");
        assertThat(result.credentials()).isNull();
    }

    @Test
    void wrongFirebaseProjectIsRejected(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("other.json");
        Files.writeString(file, serviceAccount("other-project"));
        PushProperties properties = enabled(file.toString());

        FirebaseCredentialLoader.LoadResult result = FirebaseCredentialLoader.load(properties);

        assertThat(result.credentialReadable()).isTrue();
        assertThat(result.projectStatus()).isEqualTo("MISMATCH");
        assertThat(result.reasonCode()).isEqualTo("PROJECT_MISMATCH");
        assertThat(result.credentials()).isNull();
    }

    @Test
    void matchingProjectInitializesCredentials(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("acomi.json");
        Files.writeString(file, serviceAccount(FirebaseCredentialLoader.EXPECTED_PROJECT_ID));
        PushProperties properties = enabled(file.toString());

        FirebaseCredentialLoader.LoadResult result = FirebaseCredentialLoader.load(properties);

        assertThat(result.projectStatus()).isEqualTo("MATCH");
        assertThat(result.reasonCode()).isEqualTo("READY");
        assertThat(result.credentials()).isNotNull();
    }

    @Test
    void pushDisabledDoesNotExposeCredentials(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("acomi.json");
        Files.writeString(file, serviceAccount(FirebaseCredentialLoader.EXPECTED_PROJECT_ID));
        PushProperties properties = enabled(file.toString());
        properties.setEnabled(false);

        FirebaseCredentialLoader.LoadResult result = FirebaseCredentialLoader.load(properties);

        assertThat(result.reasonCode()).isEqualTo("PUSH_DISABLED");
        assertThat(result.credentialReadable()).isTrue();
        assertThat(result.projectStatus()).isEqualTo("MATCH");
        assertThat(result.credentials()).isNull();
    }

    @Test
    void relativePathResolvesFromTheBackendModuleWhenTheWorkingDirectoryDoesNotHaveIt(
            @TempDir Path workingDirectory, @TempDir Path moduleRoot) throws Exception {
        Path file = moduleRoot.resolve("secrets").resolve("firebase-service-account.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, serviceAccount(FirebaseCredentialLoader.EXPECTED_PROJECT_ID));

        Path resolved = FirebaseCredentialLoader.resolveAgainst(
                "secrets/firebase-service-account.json", java.util.List.of(workingDirectory, moduleRoot));

        assertThat(resolved).isEqualTo(file.toAbsolutePath().normalize());
    }

    @Test
    void relativePathResolvesFromTheEnvDirectory(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("secrets").resolve("firebase-service-account.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, serviceAccount(FirebaseCredentialLoader.EXPECTED_PROJECT_ID));
        DotEnvLocation.remember(dir.resolve(".env"));
        PushProperties properties = enabled("secrets/firebase-service-account.json");

        FirebaseCredentialLoader.LoadResult result = FirebaseCredentialLoader.load(properties);

        assertThat(result.projectStatus()).isEqualTo("MATCH");
        assertThat(result.credentials()).isNotNull();
    }

    @Test
    void statusHintDoesNotIncludeAPrivateKey(@TempDir Path dir) throws Exception {
        FirebasePushStatus status = new FirebasePushStatus(true, "PATH", false, "UNKNOWN", false, "CREDENTIAL_FILE_MISSING");

        String hint = status.hint(0);

        assertThat(hint).contains("acomi-270d0");
        assertThat(hint).contains("secrets/firebase-service-account.json");
        assertThat(hint).doesNotContain("BEGIN PRIVATE KEY");
        assertThat(hint).doesNotContain(dir.toString());
        assertThat(status.fcmSendPossible(1)).isFalse();
    }

    private static PushProperties enabled(String path) {
        PushProperties properties = new PushProperties();
        properties.setEnabled(true);
        properties.setCredentialsPath(path);
        return properties;
    }

    private String serviceAccount(String projectId) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(pair.getPrivate().getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n";
        Map<String, String> account = new LinkedHashMap<>();
        account.put("type", "service_account");
        account.put("project_id", projectId);
        account.put("private_key_id", "test-only");
        account.put("private_key", pem);
        account.put("client_email", "firebase-adminsdk@" + projectId + ".iam.gserviceaccount.com");
        account.put("client_id", "1");
        account.put("token_uri", "https://oauth2.googleapis.com/token");
        return json.writeValueAsString(account);
    }
}
