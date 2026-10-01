package com.acomi.acomi_backend.location.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.storage.config.StorageProperties;
import com.acomi.acomi_backend.storage.infrastructure.provider.StorageProvider;
import com.acomi.acomi_backend.storage.infrastructure.provider.memory.InMemoryStorageProvider;
import com.acomi.acomi_backend.storage.infrastructure.provider.s3compatible.S3CompatibleStorageProvider;
import org.junit.jupiter.api.Test;

class LocationReferenceStorageConfigTest {

    private final LocationReferenceStorageConfig config = new LocationReferenceStorageConfig();

    @Test
    void reusesSharedProviderWhenAlreadyS3Compatible() {
        StorageProvider shared = new InMemoryStorageProvider();
        StorageProperties properties = new StorageProperties();
        properties.setProvider("s3compatible");

        StorageProvider selected = config.locationReferenceStorageProvider(shared, properties);

        assertThat(selected).isSameAs(shared);
    }

    @Test
    void usesDedicatedR2ClientWhenMemoryProviderHasCredentials() {
        StorageProvider memory = new InMemoryStorageProvider();
        StorageProperties properties = new StorageProperties();
        properties.setProvider("memory");
        properties.setBucket("acomi-prod-files");
        properties.getS3().setEndpoint("https://example.r2.cloudflarestorage.com");
        properties.getS3().setAccessKey("test-access");
        properties.getS3().setSecretKey("test-secret");

        StorageProvider selected = config.locationReferenceStorageProvider(memory, properties);

        assertThat(selected).isInstanceOf(S3CompatibleStorageProvider.class);
        assertThat(selected).isNotSameAs(memory);
    }

    @Test
    void keepsMemoryWhenR2CredentialsAreMissing() {
        StorageProvider memory = new InMemoryStorageProvider();
        StorageProperties properties = new StorageProperties();
        properties.setProvider("memory");

        StorageProvider selected = config.locationReferenceStorageProvider(memory, properties);

        assertThat(selected).isSameAs(memory);
        assertThat(properties.hasS3Credentials()).isFalse();
    }
}
