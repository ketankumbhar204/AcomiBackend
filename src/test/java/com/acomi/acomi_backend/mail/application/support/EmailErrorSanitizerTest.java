package com.acomi.acomi_backend.mail.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailErrorSanitizerTest {

    @Test
    void redactsPasswordFragments() {
        String sanitized = EmailErrorSanitizer.sanitize(
                new IllegalStateException("SMTP AUTH failed password=super-secret-value host=smtp.example.com"));
        assertThat(sanitized).doesNotContain("super-secret-value");
        assertThat(sanitized).contains("password=***");
        assertThat(sanitized).doesNotContain("\n");
    }
}
