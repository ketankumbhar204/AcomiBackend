package com.acomi.acomi_backend.auth.application.otp;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.config.security.OtpProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

class OtpConfigurationValidatorTest {

    @Test
    void productionCannotEnableDevSender() {
        OtpProperties properties = new OtpProperties();
        properties.setHashSecret("production-otp-hash-secret-must-be-long");
        properties.setLength(6);
        properties.setSender("dev");

        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[] {"prod"});

        OtpConfigurationValidator validator = new OtpConfigurationValidator(properties, environment);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Development OTP sender cannot be enabled in production");
    }

    @Test
    void hashSecretIsRequired() {
        OtpProperties properties = new OtpProperties();
        properties.setHashSecret("short");
        properties.setLength(6);
        properties.setSender("none");

        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[] {"prod"});

        OtpConfigurationValidator validator = new OtpConfigurationValidator(properties, environment);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("acomi.otp.hash-secret must be at least 32 characters");
    }
}
