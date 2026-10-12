package com.acomi.acomi_backend.storage.infrastructure.provider.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.storage.infrastructure.provider.DownloadGrant;
import com.acomi.acomi_backend.storage.infrastructure.provider.ObjectMetadata;
import com.acomi.acomi_backend.storage.infrastructure.provider.UploadGrant;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryStorageProviderTest {

    private InMemoryStorageProvider provider;

    @BeforeEach
    void setUp() {
        provider = new InMemoryStorageProvider();
    }

    @Test
    void putHeadDownloadAndDelete() throws Exception {
        byte[] bytes = new byte[] {1, 2, 3, 4};
        provider.putStream("bucket", "key", new ByteArrayInputStream(bytes), bytes.length, "image/jpeg");

        UploadGrant upload = provider.createUploadGrant(
                "bucket", "key", "image/jpeg", bytes.length, Duration.ofMinutes(5), Map.of());
        assertThat(upload.url()).startsWith("memory://");
        assertThat(upload.method()).isEqualTo("PUT");

        ObjectMetadata metadata = provider.head("bucket", "key");
        assertThat(metadata.exists()).isTrue();
        assertThat(metadata.byteSize()).isEqualTo(4);
        assertThat(metadata.contentType()).isEqualTo("image/jpeg");

        DownloadGrant download = provider.createDownloadGrant("bucket", "key", Duration.ofMinutes(2));
        assertThat(download.url()).isEqualTo("memory://bucket/key");

        assertThat(provider.openStream("bucket", "key").readAllBytes()).isEqualTo(bytes);
        assertThat(provider.getRange("bucket", "key", 1, 2)).containsExactly(2, 3);

        provider.delete("bucket", "key");
        assertThat(provider.head("bucket", "key").exists()).isFalse();
        assertThatThrownBy(() -> provider.openStream("bucket", "key")).isInstanceOf(BusinessException.class);
    }

    @Test
    void missingObjectIsNotAnErrorOnHead() {
        assertThat(provider.head("bucket", "missing").exists()).isFalse();
    }

    @Test
    void doesNotSupportDirectBrowserUpload() {
        assertThat(provider.supportsDirectBrowserUpload()).isFalse();
    }

    @Test
    void reloadsObjectsAfterRestart() throws Exception {
        Path dir = Files.createTempDirectory("acomi-storage");
        byte[] bytes = new byte[] {9, 8, 7};
        InMemoryStorageProvider first = new InMemoryStorageProvider(dir);
        first.putStream("bucket", "qr/image", new ByteArrayInputStream(bytes), bytes.length, "image/png");

        InMemoryStorageProvider restarted = new InMemoryStorageProvider(dir);
        assertThat(restarted.head("bucket", "qr/image").exists()).isTrue();
        assertThat(restarted.head("bucket", "qr/image").contentType()).isEqualTo("image/png");
        assertThat(restarted.openStream("bucket", "qr/image").readAllBytes()).isEqualTo(bytes);
    }
}
