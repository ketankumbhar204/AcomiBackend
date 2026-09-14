package com.acomi.acomi_backend.storage.config;

import com.acomi.acomi_backend.storage.infrastructure.provider.StorageProvider;
import com.acomi.acomi_backend.storage.infrastructure.provider.memory.InMemoryStorageProvider;
import com.acomi.acomi_backend.storage.infrastructure.provider.s3compatible.S3CompatibleStorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class StorageProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageProviderConfig.class);

    @Bean
    public StorageProvider storageProvider(StorageProperties properties) {
        if (properties.isS3Compatible()) {
            StorageProperties.S3 s3 = properties.getS3();
            if (!StringUtils.hasText(s3.getEndpoint())
                    || !StringUtils.hasText(s3.getAccessKey())
                    || !StringUtils.hasText(s3.getSecretKey())
                    || !StringUtils.hasText(properties.getBucket())) {
                throw new IllegalStateException(
                        "acomi.storage.provider=s3compatible requires endpoint, bucket, access-key, and secret-key");
            }
            log.info(
                    "storage_provider_selected adapter=s3compatible logicalName={} bucketConfigured=true",
                    properties.getLogicalName());
            return new S3CompatibleStorageProvider(properties);
        }
        log.info("storage_provider_selected adapter=memory logicalName={}", properties.getLogicalName());
        return new InMemoryStorageProvider();
    }
}
