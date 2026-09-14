package com.acomi.acomi_backend.notification.infrastructure.firebase;

import com.acomi.acomi_backend.notification.config.PushProperties;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Initializes the Firebase Admin SDK from environment-supplied credentials.
 * Never logs credential contents. Missing/invalid config disables FCM safely.
 */
@Slf4j
@Configuration
public class FirebaseConfig {

    @Bean
    public FirebaseMessaging firebaseMessaging(PushProperties properties) {
        if (!properties.isEnabled()) {
            log.info("FCM skipped: acomi.push.enabled=false");
            return null;
        }
        try {
            GoogleCredentials credentials = loadCredentials(properties);
            if (credentials == null) {
                log.warn("FCM skipped: ACOMI_PUSH_ENABLED is true but no credentials were provided");
                return null;
            }
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(
                        FirebaseOptions.builder().setCredentials(credentials).build());
            }
            log.info("Firebase Admin SDK initialized for FCM");
            return FirebaseMessaging.getInstance();
        } catch (Exception ex) {
            log.error("FCM initialization failed; push delivery disabled. {}", ex.getMessage());
            return null;
        }
    }

    private static GoogleCredentials loadCredentials(PushProperties properties) throws Exception {
        String json = properties.getCredentialsJson();
        if (json != null && !json.isBlank()) {
            try (InputStream in = new ByteArrayInputStream(json.trim().getBytes(StandardCharsets.UTF_8))) {
                return GoogleCredentials.fromStream(in);
            }
        }
        String path = properties.getCredentialsPath();
        if (path != null && !path.isBlank()) {
            Path file = Path.of(path.trim());
            if (!Files.isRegularFile(file)) {
                log.warn("FCM skipped: credentials file is not readable");
                return null;
            }
            try (InputStream in = Files.newInputStream(file)) {
                return GoogleCredentials.fromStream(in);
            }
        }
        return null;
    }
}
