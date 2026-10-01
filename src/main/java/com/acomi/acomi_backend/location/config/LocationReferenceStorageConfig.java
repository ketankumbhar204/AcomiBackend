package com.acomi.acomi_backend.location.config;

import com.acomi.acomi_backend.storage.config.StorageProperties;
import com.acomi.acomi_backend.storage.infrastructure.provider.StorageProvider;
import com.acomi.acomi_backend.storage.infrastructure.provider.s3compatible.S3CompatibleStorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Location search always reads {@code reference/locations.json} from R2/S3 when
 * credentials exist. Local file uploads can stay on the in-memory provider.
 */
@Configuration
public class LocationReferenceStorageConfig {

    private static final Logger log = LoggerFactory.getLogger(LocationReferenceStorageConfig.class);

    @Bean(name = "locationReferenceStorageProvider")
    public StorageProvider locationReferenceStorageProvider(
            StorageProvider storageProvider, StorageProperties properties) {
        if (properties.isS3Compatible()) {
            log.info("location_reference_storage adapter=s3compatible mode=shared");
            return storageProvider;
        }
        if (properties.hasS3Credentials()) {
            log.info("location_reference_storage adapter=s3compatible mode=dedicated");
            return new S3CompatibleStorageProvider(properties);
        }
        log.info("location_reference_storage adapter={}", properties.getProvider());
        return storageProvider;
    }
}
