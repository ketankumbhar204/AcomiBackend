package com.acomi.acomi_backend.enquiry.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WhatsAppClickToChatTest {

    @Test
    void normalizesIndianAndInternationalNumbers() {
        assertThat(WhatsAppClickToChat.digits("9876543210")).isEqualTo("919876543210");
        assertThat(WhatsAppClickToChat.digits("+91 98765 43210")).isEqualTo("919876543210");
        assertThat(WhatsAppClickToChat.digits("09876543210")).isEqualTo("919876543210");
        assertThat(WhatsAppClickToChat.digits("00919876543210")).isEqualTo("919876543210");
        assertThat(WhatsAppClickToChat.digits("+1 415 555 2671")).isEqualTo("14155552671");
        assertThat(WhatsAppClickToChat.digits("123")).isNull();
        assertThat(WhatsAppClickToChat.digits(null)).isNull();
        assertThat(WhatsAppClickToChat.digits("   ")).isNull();
    }
}
