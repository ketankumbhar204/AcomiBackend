package com.acomi.acomi_backend.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Push / FCM settings. Service-account JSON must come from the environment —
 * never from committed YAML or the mobile app.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "acomi.push")
public class PushProperties {

    /**
     * Master switch. When false, in-app notifications still persist and business
     * transactions continue; FCM is skipped.
     */
    private boolean enabled = false;

    /**
     * Entire Firebase service-account JSON as a single env value.
     * Env: {@code FIREBASE_CREDENTIALS_JSON}.
     */
    private String credentialsJson;

    /**
     * Absolute path to a service-account JSON file on the host.
     * Env: {@code FIREBASE_CREDENTIALS_PATH}. Used only when {@link #credentialsJson} is blank.
     */
    private String credentialsPath;
}
