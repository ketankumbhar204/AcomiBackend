package com.acomi.acomi_backend.storage.infrastructure.provider.s3compatible;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.storage.config.StorageProperties;
import com.acomi.acomi_backend.storage.domain.model.FileErrorCodes;
import com.acomi.acomi_backend.storage.infrastructure.provider.DownloadGrant;
import com.acomi.acomi_backend.storage.infrastructure.provider.ObjectMetadata;
import com.acomi.acomi_backend.storage.infrastructure.provider.StorageProvider;
import com.acomi.acomi_backend.storage.infrastructure.provider.UploadGrant;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * S3-compatible adapter used for Cloudflare R2 today and AWS S3 / MinIO later.
 * Domain code must depend only on {@link StorageProvider}.
 */
public class S3CompatibleStorageProvider implements StorageProvider {

    private static final Logger log = LoggerFactory.getLogger(S3CompatibleStorageProvider.class);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    public S3CompatibleStorageProvider(StorageProperties properties) {
        StorageProperties.S3 s3 = properties.getS3();
        URI endpoint = URI.create(s3.getEndpoint().trim());
        Region region = Region.of(s3.getRegion() == null || s3.getRegion().isBlank() ? "auto" : s3.getRegion().trim());
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(s3.getAccessKey(), s3.getSecretKey()));
        S3Configuration serviceConfig = S3Configuration.builder()
                .pathStyleAccessEnabled(s3.isPathStyle())
                .chunkedEncodingEnabled(false)
                .build();
        this.s3Client = S3Client.builder()
                .endpointOverride(endpoint)
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(serviceConfig)
                .build();
        this.s3Presigner = S3Presigner.builder()
                .endpointOverride(endpoint)
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(serviceConfig)
                .build();
    }

    S3CompatibleStorageProvider(S3Client s3Client, S3Presigner s3Presigner) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    @Override
    public UploadGrant createUploadGrant(
            String bucket,
            String objectKey,
            String contentType,
            long contentLength,
            Duration ttl,
            Map<String, String> extraHeaders) {
        try {
            PutObjectRequest.Builder put = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(contentType)
                    .contentLength(contentLength);
            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .putObjectRequest(put.build())
                    .build();
            PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
            Map<String, String> headers = new LinkedHashMap<>();
            presigned.signedHeaders().forEach((name, values) -> {
                if (values != null && !values.isEmpty()) {
                    headers.put(name, values.get(0));
                }
            });
            headers.putIfAbsent("Content-Type", contentType);
            return new UploadGrant(
                    presigned.url().toString(), "PUT", Map.copyOf(headers), LocalDateTime.now().plus(ttl));
        } catch (RuntimeException ex) {
            log.error("storage_upload_grant_failed keyPresent=true", ex);
            throw storageError();
        }
    }

    @Override
    public DownloadGrant createDownloadGrant(String bucket, String objectKey, Duration ttl) {
        try {
            GetObjectRequest get = GetObjectRequest.builder().bucket(bucket).key(objectKey).build();
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .getObjectRequest(get)
                    .build();
            PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
            return new DownloadGrant(presigned.url().toString(), LocalDateTime.now().plus(ttl));
        } catch (RuntimeException ex) {
            log.error("storage_download_grant_failed keyPresent=true", ex);
            throw storageError();
        }
    }

    @Override
    public ObjectMetadata head(String bucket, String objectKey) {
        try {
            HeadObjectResponse response = s3Client.headObject(
                    HeadObjectRequest.builder().bucket(bucket).key(objectKey).build());
            Long length = response.contentLength();
            return new ObjectMetadata(
                    true,
                    length == null ? 0 : length,
                    response.contentType(),
                    response.checksumSHA256());
        } catch (NoSuchKeyException ex) {
            return ObjectMetadata.missing();
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return ObjectMetadata.missing();
            }
            log.error("storage_head_failed status={}", ex.statusCode());
            throw storageError();
        }
    }

    @Override
    public void delete(String bucket, String objectKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
        } catch (RuntimeException ex) {
            log.error("storage_delete_failed keyPresent=true", ex);
            throw storageError();
        }
    }

    @Override
    public InputStream openStream(String bucket, String objectKey) {
        try {
            return s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(objectKey).build());
        } catch (NoSuchKeyException ex) {
            throw new BusinessException(
                    FileErrorCodes.FILE_NOT_FOUND, "Stored object was not found", HttpStatus.NOT_FOUND);
        } catch (RuntimeException ex) {
            log.error("storage_open_failed keyPresent=true", ex);
            throw storageError();
        }
    }

    @Override
    public byte[] getRange(String bucket, String objectKey, long startInclusive, long endInclusive) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .range("bytes=" + startInclusive + "-" + endInclusive)
                    .build();
            return s3Client.getObjectAsBytes(request).asByteArray();
        } catch (NoSuchKeyException ex) {
            throw new BusinessException(
                    FileErrorCodes.FILE_NOT_FOUND, "Stored object was not found", HttpStatus.NOT_FOUND);
        } catch (RuntimeException ex) {
            log.error("storage_range_failed keyPresent=true", ex);
            throw storageError();
        }
    }

    @Override
    public void putStream(
            String bucket,
            String objectKey,
            InputStream stream,
            long contentLength,
            String contentType) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(contentType)
                    .contentLength(contentLength)
                    .build();
            s3Client.putObject(request, RequestBody.fromInputStream(stream, contentLength));
        } catch (RuntimeException ex) {
            log.error("storage_put_failed keyPresent=true", ex);
            throw storageError();
        }
    }

    @Override
    public boolean supportsDirectBrowserUpload() {
        return true;
    }

    private static BusinessException storageError() {
        return new BusinessException(
                FileErrorCodes.FILE_STORAGE_ERROR,
                "File storage is temporarily unavailable",
                HttpStatus.BAD_GATEWAY);
    }
}
