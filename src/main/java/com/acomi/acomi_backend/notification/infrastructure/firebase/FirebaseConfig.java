package com.acomi.acomi_backend.notification.infrastructure.firebase;

import com.acomi.acomi_backend.notification.config.PushProperties;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Initializes Firebase Admin once from backend-only credentials.
 * Never logs credential contents or filesystem paths.
 */
@Slf4j
@Configuration
public class FirebaseConfig {

    @Bean
    public FirebasePushStatus firebasePushStatus(PushProperties properties) {
        FirebaseCredentialLoader.LoadResult loaded = FirebaseCredentialLoader.load(properties);
        boolean ready = false;
        String reason = loaded.reasonCode();
        if (properties.isEnabled() && loaded.credentials() != null && "MATCH".equals(loaded.projectStatus())) {
            try {
                initializeOnce(loaded.credentials());
                ready = true;
                reason = "READY";
                log.info("Firebase Admin SDK initialized for FCM project={}", FirebaseCredentialLoader.EXPECTED_PROJECT_ID);
            } catch (Exception ex) {
                reason = "INIT_FAILED";
                log.error("FCM initialization failed; push delivery disabled. type={}", ex.getClass().getSimpleName());
            }
        } else if (!properties.isEnabled()) {
            log.info("FCM skipped: acomi.push.enabled=false");
        } else {
            log.warn("FCM skipped: reason={}", reason);
        }
        return new FirebasePushStatus(
                properties.isEnabled(),
                loaded.credentialSource(),
                loaded.credentialReadable(),
                loaded.projectStatus(),
                ready,
                reason);
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebasePushStatus status) {
        if (!status.firebaseReady() || FirebaseApp.getApps().isEmpty()) {
            log.info("FCM messaging provider unavailable reason={}", status.reasonCode());
            return null;
        }
        log.info("FCM messaging provider available project={}", FirebaseCredentialLoader.EXPECTED_PROJECT_ID);
        return FirebaseMessaging.getInstance();
    }

    static void initializeOnce(GoogleCredentials credentials) {
        synchronized (FirebaseConfig.class) {
            if (!FirebaseApp.getApps().isEmpty()) {
                String existing = FirebaseApp.getInstance().getOptions().getProjectId();
                if (existing != null && !FirebaseCredentialLoader.EXPECTED_PROJECT_ID.equals(existing)) {
                    throw new IllegalStateException("Firebase app is already initialized for a different project");
                }
                log.info("Firebase Admin already initialized for project={}", FirebaseCredentialLoader.EXPECTED_PROJECT_ID);
                return;
            }
            FirebaseApp.initializeApp(FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .setProjectId(FirebaseCredentialLoader.EXPECTED_PROJECT_ID)
                    .build());
        }
    }
}
