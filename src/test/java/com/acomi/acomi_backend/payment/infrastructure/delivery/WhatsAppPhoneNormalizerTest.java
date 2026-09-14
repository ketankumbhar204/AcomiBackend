package com.acomi.acomi_backend.payment.infrastructure.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WhatsAppPhoneNormalizerTest {

    private final WhatsAppReminderProperties properties = new WhatsAppReminderProperties();
    private final WhatsAppPhoneNormalizer normalizer = new WhatsAppPhoneNormalizer(properties);

    @Test
    void tenDigitIndia_normalized() {
        var result = normalizer.normalize("9876543210");
        assertThat(result).isPresent();
        assertThat(result.get().digitsE164WithoutPlus()).isEqualTo("919876543210");
        assertThat(result.get().maskedForLog()).isEqualTo("****3210");
    }

    @Test
    void alreadyInternational_kept() {
        var result = normalizer.normalize("+91 98765 43210");
        assertThat(result).isPresent();
        assertThat(result.get().digitsE164WithoutPlus()).isEqualTo("919876543210");
    }

    @Test
    void blank_rejected() {
        assertThat(normalizer.normalize("  ")).isEmpty();
        assertThat(normalizer.normalize(null)).isEmpty();
    }

    @Test
    void malformed_rejected() {
        assertThat(normalizer.normalize("12345")).isEmpty();
        assertThat(normalizer.normalize("abcdef")).isEmpty();
    }
}
