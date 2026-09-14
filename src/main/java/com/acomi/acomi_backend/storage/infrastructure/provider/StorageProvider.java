package com.acomi.acomi_backend.storage.infrastructure.provider;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;

/**
 * Provider-neutral object storage port. Domain code must not depend on R2/S3 SDKs.
 */
public interface StorageProvider {

    UploadGrant createUploadGrant(
            String bucket,
            String objectKey,
            String contentType,
            long contentLength,
            Duration ttl,
            Map<String, String> extraHeaders);

    DownloadGrant createDownloadGrant(String bucket, String objectKey, Duration ttl);

    ObjectMetadata head(String bucket, String objectKey);

    void delete(String bucket, String objectKey);

    InputStream openStream(String bucket, String objectKey);

    byte[] getRange(String bucket, String objectKey, long startInclusive, long endInclusive);

    void putStream(
            String bucket,
            String objectKey,
            InputStream stream,
            long contentLength,
            String contentType);

    boolean supportsDirectBrowserUpload();
}
