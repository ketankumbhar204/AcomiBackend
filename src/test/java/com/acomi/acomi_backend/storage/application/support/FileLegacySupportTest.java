package com.acomi.acomi_backend.storage.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FileLegacySupportTest {

    @Test
    void markerRoundTrip() {
        UUID id = UUID.randomUUID();
        String marker = FileLegacySupport.marker(id);
        assertThat(FileLegacySupport.isMarker(marker)).isTrue();
        assertThat(FileLegacySupport.parseMarker(marker)).isEqualTo(id);
        assertThat(FileLegacySupport.isDisplayableLegacy(marker)).isFalse();
    }

    @Test
    void keepsHttpAndInlineReadable() {
        assertThat(FileLegacySupport.isHttpUrl("https://cdn.example/photo.jpg")).isTrue();
        assertThat(FileLegacySupport.isInlinePayload("data:image/jpeg;base64,/9j/4AAQ")).isTrue();
        assertThat(FileLegacySupport.isDisplayableLegacy("https://cdn.example/photo.jpg")).isTrue();
        assertThat(FileLegacySupport.isPendingPlaceholder("pending-upload")).isTrue();
    }

    @Test
    void decodesDataUri() {
        byte[] raw = new byte[] {1, 2, 3, 4};
        String payload = "data:image/png;base64," + Base64.getEncoder().encodeToString(raw);
        var decoded = FileLegacySupport.decodeImagePayload(payload);
        assertThat(decoded.contentType()).isEqualTo("image/png");
        assertThat(decoded.bytes()).isEqualTo(raw);
    }
}
