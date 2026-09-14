package com.acomi.acomi_backend.mail.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailTextSanitizerTest {

    @Test
    void subjectStripsCrLfInjection() {
        String subject = EmailTextSanitizer.subject("ACOMI – Contact details for Sunrise\r\nBcc: attacker@example.com");
        assertThat(subject).doesNotContain("\r").doesNotContain("\n");
        assertThat(subject).contains("Sunrise");
        assertThat(subject).contains("Bcc: attacker@example.com");
    }

    @Test
    void headerFlattensControlCharacters() {
        assertThat(EmailTextSanitizer.header("support@acomi.in\nFrom: evil@example.com"))
                .isEqualTo("support@acomi.in From: evil@example.com");
    }

    @Test
    void htmlEscapesMarkup() {
        assertThat(EmailTextSanitizer.html("Lovely Home's PG <script>")).isEqualTo("Lovely Home&#39;s PG &lt;script&gt;");
        assertThat(EmailTextSanitizer.html("A & B")).isEqualTo("A &amp; B");
    }
}
