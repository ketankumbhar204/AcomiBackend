package com.acomi.acomi_backend.notification.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenMaskTest {

    @Test
    void masksLongTokens() {
        assertThat(TokenMask.mask("abcdefghijklmnop")).isEqualTo("abcdef…mnop");
    }

    @Test
    void handlesBlank() {
        assertThat(TokenMask.mask("")).isEqualTo("(empty)");
    }
}
