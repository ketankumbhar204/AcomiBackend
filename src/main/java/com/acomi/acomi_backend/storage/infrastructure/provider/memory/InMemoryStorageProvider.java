package com.acomi.acomi_backend.storage.infrastructure.provider.memory;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.storage.domain.model.FileErrorCodes;
import com.acomi.acomi_backend.storage.infrastructure.provider.DownloadGrant;
import com.acomi.acomi_backend.storage.infrastructure.provider.ObjectMetadata;
import com.acomi.acomi_backend.storage.infrastructure.provider.StorageProvider;
import com.acomi.acomi_backend.storage.infrastructure.provider.UploadGrant;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;

/**
 * In-process object store for unit tests and local development without R2 credentials.
 * Direct uploads use ACOMI {@code PUT /api/v1/files/{id}/content} rather than a signed provider URL.
 */
public class InMemoryStorageProvider implements StorageProvider {

    private record StoredObject(byte[] bytes, String contentType) {}

    private final ConcurrentHashMap<String, StoredObject> objects = new ConcurrentHashMap<>();

    @Override
    public UploadGrant createUploadGrant(
            String bucket,
            String objectKey,
            String contentType,
            long contentLength,
            Duration ttl,
            Map<String, String> extraHeaders) {
        return new UploadGrant(
                "memory://" + bucket + "/" + objectKey,
                "PUT",
                Map.of("Content-Type", contentType),
                LocalDateTime.now().plus(ttl));
    }

    @Override
    public DownloadGrant createDownloadGrant(String bucket, String objectKey, Duration ttl) {
        return new DownloadGrant("memory://" + bucket + "/" + objectKey, LocalDateTime.now().plus(ttl));
    }

    @Override
    public ObjectMetadata head(String bucket, String objectKey) {
        StoredObject object = objects.get(key(bucket, objectKey));
        if (object == null) {
            return ObjectMetadata.missing();
        }
        return new ObjectMetadata(true, object.bytes().length, object.contentType(), null);
    }

    @Override
    public void delete(String bucket, String objectKey) {
        objects.remove(key(bucket, objectKey));
    }

    @Override
    public InputStream openStream(String bucket, String objectKey) {
        StoredObject object = require(bucket, objectKey);
        return new ByteArrayInputStream(object.bytes());
    }

    @Override
    public byte[] getRange(String bucket, String objectKey, long startInclusive, long endInclusive) {
        StoredObject object = require(bucket, objectKey);
        int start = (int) Math.max(0, startInclusive);
        int end = (int) Math.min(object.bytes().length - 1, endInclusive);
        if (start > end) {
            return new byte[0];
        }
        return Arrays.copyOfRange(object.bytes(), start, end + 1);
    }

    @Override
    public void putStream(
            String bucket,
            String objectKey,
            InputStream stream,
            long contentLength,
            String contentType) {
        try {
            byte[] bytes = readAll(stream, contentLength);
            objects.put(key(bucket, objectKey), new StoredObject(bytes, contentType));
        } catch (IOException ex) {
            throw new BusinessException(
                    FileErrorCodes.FILE_STORAGE_ERROR, "Unable to store file", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public boolean supportsDirectBrowserUpload() {
        return false;
    }

    public void clear() {
        objects.clear();
    }

    private StoredObject require(String bucket, String objectKey) {
        StoredObject object = objects.get(key(bucket, objectKey));
        if (object == null) {
            throw new BusinessException(
                    FileErrorCodes.FILE_NOT_FOUND, "Stored object was not found", HttpStatus.NOT_FOUND);
        }
        return object;
    }

    private static String key(String bucket, String objectKey) {
        return bucket + "/" + objectKey;
    }

    private static byte[] readAll(InputStream stream, long contentLength) throws IOException {
        if (contentLength >= 0 && contentLength <= Integer.MAX_VALUE) {
            return stream.readNBytes((int) contentLength);
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        stream.transferTo(buffer);
        return buffer.toByteArray();
    }
}
