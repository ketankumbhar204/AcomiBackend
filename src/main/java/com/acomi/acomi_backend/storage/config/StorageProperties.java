package com.acomi.acomi_backend.storage.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Provider-neutral storage settings. Secrets come from the environment, never source.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "acomi.storage")
public class StorageProperties {

    /**
     * When false, file APIs return FILE_STORAGE_UNAVAILABLE.
     */
    private boolean enabled = true;

    /**
     * Adapter selector: {@code memory} or {@code s3compatible}.
     */
    private String provider = "memory";

    /**
     * Logical provider name persisted on {@code stored_files.storage_provider}
     * (e.g. r2, s3, minio, memory). Not a URL.
     */
    private String logicalName = "memory";

    private String bucket = "acomi-files";

    private int uploadUrlTtlSeconds = 900;

    private int downloadUrlTtlSeconds = 120;

    private int pendingTtlSeconds = 900;

    private int unassociatedGraceDays = 7;

    private int purgeDelayDays = 30;

    private int maxUploadSessionsPerHour = 20;

    private S3 s3 = new S3();

    public boolean isS3Compatible() {
        return "s3compatible".equalsIgnoreCase(provider);
    }

    @Getter
    @Setter
    public static class S3 {
        private String endpoint = "";
        private String region = "auto";
        private boolean pathStyle = true;
        private String accessKey = "";
        private String secretKey = "";
    }
}
