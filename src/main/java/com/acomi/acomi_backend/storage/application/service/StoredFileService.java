package com.acomi.acomi_backend.storage.application.service;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.exception.RateLimitedException;
import com.acomi.acomi_backend.storage.api.dto.request.CreateUploadSessionRequest;
import com.acomi.acomi_backend.storage.api.dto.response.ContentUrlResponse;
import com.acomi.acomi_backend.storage.api.dto.response.StoredFileResponse;
import com.acomi.acomi_backend.storage.api.dto.response.UploadSessionResponse;
import com.acomi.acomi_backend.storage.application.support.FileLegacySupport;
import com.acomi.acomi_backend.storage.application.support.FileLegacySupport.DecodedImage;
import com.acomi.acomi_backend.storage.application.support.FileValidationService;
import com.acomi.acomi_backend.storage.application.support.ObjectKeyFactory;
import com.acomi.acomi_backend.storage.config.StorageProperties;
import com.acomi.acomi_backend.storage.domain.model.FileErrorCodes;
import com.acomi.acomi_backend.storage.domain.model.FilePurpose;
import com.acomi.acomi_backend.storage.domain.model.FileStatus;
import com.acomi.acomi_backend.storage.domain.model.FileVisibility;
import com.acomi.acomi_backend.storage.infrastructure.persistence.entity.StoredFileEntity;
import com.acomi.acomi_backend.storage.infrastructure.persistence.repository.StoredFileRepository;
import com.acomi.acomi_backend.storage.infrastructure.provider.DownloadGrant;
import com.acomi.acomi_backend.storage.infrastructure.provider.ObjectMetadata;
import com.acomi.acomi_backend.storage.infrastructure.provider.StorageProvider;
import com.acomi.acomi_backend.storage.infrastructure.provider.UploadGrant;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class StoredFileService {

    private static final Logger log = LoggerFactory.getLogger(StoredFileService.class);

    private final StoredFileRepository storedFileRepository;
    private final StorageProvider storageProvider;
    private final StorageProperties storageProperties;
    private final FileAuthorizationService fileAuthorizationService;
    private final FileValidationService fileValidationService;

    @Transactional
    public UploadSessionResponse createUploadSession(UUID callerId, CreateUploadSessionRequest request) {
        assertEnabled();
        enforceSessionRateLimit(callerId);
        fileAuthorizationService.authorizeCreateSession(callerId, request);

        FilePurpose purpose = request.getPurpose();
        String contentType = fileValidationService.normalizeContentType(request.getContentType());
        fileValidationService.validateSize(purpose, request.getByteSize());
        String filename = fileValidationService.sanitizeFilename(request.getOriginalFilename());

        LocalDateTime now = LocalDateTime.now();
        StoredFileEntity entity = StoredFileEntity.builder()
                .purpose(purpose)
                .visibility(FileVisibility.PRIVATE)
                .status(FileStatus.PENDING)
                .originalFilename(filename)
                .contentType(contentType)
                .byteSize(request.getByteSize())
                .checksumSha256(normalizeChecksum(request.getChecksumSha256()))
                .checksumVerified(false)
                .storageProvider(storageProperties.getLogicalName())
                .bucket(storageProperties.getBucket())
                .objectKey("pending/" + UUID.randomUUID())
                .uploadedByUserId(callerId)
                .spaceId(purpose.requiresSpace() ? request.getSpaceId() : null)
                .associated(false)
                .expiresAt(now.plusSeconds(storageProperties.getPendingTtlSeconds()))
                .build();
        entity = persistWithCanonicalObjectKey(entity, purpose);

        UUID fileId = entity.getId();
        boolean proxy = !storageProvider.supportsDirectBrowserUpload();
        UploadGrant grant = storageProvider.createUploadGrant(
                entity.getBucket(),
                entity.getObjectKey(),
                contentType,
                request.getByteSize(),
                Duration.ofSeconds(storageProperties.getUploadUrlTtlSeconds()),
                Map.of());

        log.info(
                "file_upload_session_created fileId={} purpose={} userId={} spaceId={} size={}",
                fileId,
                purpose,
                callerId,
                entity.getSpaceId(),
                request.getByteSize());

        return UploadSessionResponse.builder()
                .fileId(fileId)
                .purpose(purpose)
                .status(FileStatus.PENDING)
                .uploadUrl(proxy ? "/files/" + fileId + "/content" : grant.url())
                .uploadMethod("PUT")
                .uploadHeaders(grant.headers())
                .expiresAt(entity.getExpiresAt())
                .useAcomiUploadProxy(proxy)
                .build();
    }

    @Transactional
    public void storePendingContent(UUID callerId, UUID fileId, InputStream body, long contentLength) {
        assertEnabled();
        StoredFileEntity file = load(fileId);
        fileAuthorizationService.authorizeComplete(callerId, file);
        if (file.getStatus() != FileStatus.PENDING) {
            throw new BusinessException(
                    FileErrorCodes.UPLOAD_NOT_COMPLETED, "File is not awaiting upload", HttpStatus.BAD_REQUEST);
        }
        if (file.getExpiresAt() != null && file.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(
                    FileErrorCodes.UPLOAD_EXPIRED, "Upload session has expired", HttpStatus.BAD_REQUEST);
        }
        if (file.getByteSize() != null && contentLength > 0 && contentLength != file.getByteSize()) {
            throw new BusinessException(
                    FileErrorCodes.FILE_TOO_LARGE, "Uploaded size does not match the session", HttpStatus.BAD_REQUEST);
        }
        storageProvider.putStream(
                file.getBucket(), file.getObjectKey(), body, contentLength > 0 ? contentLength : file.getByteSize(), file.getContentType());
    }

    @Transactional
    public StoredFileResponse complete(UUID callerId, UUID fileId) {
        assertEnabled();
        StoredFileEntity file = load(fileId);
        fileAuthorizationService.authorizeComplete(callerId, file);

        if (file.getStatus() == FileStatus.ACTIVE) {
            return toResponse(file);
        }
        if (file.getStatus() != FileStatus.PENDING) {
            throw new BusinessException(
                    FileErrorCodes.UPLOAD_NOT_COMPLETED, "File is not awaiting upload", HttpStatus.BAD_REQUEST);
        }
        if (file.getExpiresAt() != null && file.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(
                    FileErrorCodes.UPLOAD_EXPIRED, "Upload session has expired", HttpStatus.BAD_REQUEST);
        }

        ObjectMetadata metadata = storageProvider.head(file.getBucket(), file.getObjectKey());
        if (!metadata.exists()) {
            throw new BusinessException(
                    FileErrorCodes.UPLOAD_NOT_COMPLETED, "File has not been uploaded yet", HttpStatus.BAD_REQUEST);
        }
        if (file.getByteSize() != null && metadata.byteSize() != file.getByteSize()) {
            throw new BusinessException(
                    FileErrorCodes.FILE_TOO_LARGE, "Uploaded size does not match the session", HttpStatus.BAD_REQUEST);
        }
        byte[] prefix = storageProvider.getRange(file.getBucket(), file.getObjectKey(), 0, 15);
        fileValidationService.validateMagicBytes(file.getContentType(), prefix);
        String checksum = sha256(file);
        if (file.getChecksumSha256() != null && !file.getChecksumSha256().equalsIgnoreCase(checksum)) {
            throw new BusinessException(
                    FileErrorCodes.FILE_TYPE_NOT_ALLOWED, "File checksum does not match", HttpStatus.BAD_REQUEST);
        }

        file.setByteSize(metadata.byteSize());
        file.setChecksumSha256(checksum);
        file.setChecksumVerified(true);
        file.setStatus(FileStatus.ACTIVE);
        file.setExpiresAt(null);
        storedFileRepository.save(file);

        log.info(
                "file_upload_completed fileId={} purpose={} userId={} size={}",
                file.getId(),
                file.getPurpose(),
                callerId,
                file.getByteSize());
        return toResponse(file);
    }

    @Transactional(readOnly = true)
    public StoredFileResponse getMetadata(UUID callerId, UUID fileId) {
        StoredFileEntity file = load(fileId);
        fileAuthorizationService.authorizeRead(callerId, file);
        return toResponse(file);
    }

    @Transactional(readOnly = true)
    public ContentUrlResponse createContentUrl(UUID callerId, UUID fileId) {
        assertEnabled();
        StoredFileEntity file = load(fileId);
        fileAuthorizationService.authorizeRead(callerId, file);
        if (file.getStatus() != FileStatus.ACTIVE) {
            throw new BusinessException(
                    FileErrorCodes.UPLOAD_NOT_COMPLETED, "File is not available", HttpStatus.BAD_REQUEST);
        }
        Duration ttl = Duration.ofSeconds(storageProperties.getDownloadUrlTtlSeconds());
        DownloadGrant grant = storageProvider.createDownloadGrant(file.getBucket(), file.getObjectKey(), ttl);
        String url = grant.url();
        if (!storageProvider.supportsDirectBrowserUpload()) {
            url = "/files/" + file.getId() + "/content";
        }
        log.info("file_download_url_issued fileId={} purpose={} userId={}", file.getId(), file.getPurpose(), callerId);
        return ContentUrlResponse.builder()
                .fileId(file.getId())
                .contentUrl(url)
                .contentType(file.getContentType())
                .expiresAt(LocalDateTime.now().plus(ttl))
                .build();
    }

    @Transactional(readOnly = true)
    public byte[] readActiveContent(UUID callerId, UUID fileId) {
        StoredFileEntity file = load(fileId);
        fileAuthorizationService.authorizeRead(callerId, file);
        if (file.getStatus() != FileStatus.ACTIVE && file.getStatus() != FileStatus.PENDING) {
            throw new BusinessException(
                    FileErrorCodes.FILE_NOT_FOUND, "File is not available", HttpStatus.NOT_FOUND);
        }
        try (InputStream in = storageProvider.openStream(file.getBucket(), file.getObjectKey())) {
            return in.readAllBytes();
        } catch (IOException ex) {
            throw new BusinessException(
                    FileErrorCodes.FILE_STORAGE_ERROR, "Unable to read file", HttpStatus.BAD_GATEWAY);
        }
    }

    @Transactional
    public void delete(UUID callerId, UUID fileId) {
        StoredFileEntity file = load(fileId);
        fileAuthorizationService.authorizeDelete(callerId, file);
        scheduleDelete(file);
        log.info("file_delete_requested fileId={} purpose={} userId={}", file.getId(), file.getPurpose(), callerId);
    }

    @Transactional
    public StoredFileEntity ingestImage(
            UUID callerId, FilePurpose purpose, UUID spaceId, String imagePayload, String originalFilename) {
        assertEnabled();
        DecodedImage decoded = FileLegacySupport.decodeImagePayload(imagePayload);
        if (decoded == null || decoded.bytes() == null || decoded.bytes().length == 0) {
            throw new BusinessException(
                    FileErrorCodes.FILE_TYPE_NOT_ALLOWED, "Image payload is invalid", HttpStatus.BAD_REQUEST);
        }
        String contentType = fileValidationService.normalizeContentType(decoded.contentType());
        fileValidationService.validateSize(purpose, decoded.bytes().length);
        fileValidationService.validateMagicBytes(
                contentType,
                decoded.bytes().length >= 12
                        ? java.util.Arrays.copyOf(decoded.bytes(), 12)
                        : decoded.bytes());

        StoredFileEntity entity = StoredFileEntity.builder()
                .purpose(purpose)
                .visibility(FileVisibility.PRIVATE)
                .status(FileStatus.ACTIVE)
                .originalFilename(fileValidationService.sanitizeFilename(originalFilename))
                .contentType(contentType)
                .byteSize((long) decoded.bytes().length)
                .checksumSha256(sha256(decoded.bytes()))
                .checksumVerified(true)
                .storageProvider(storageProperties.getLogicalName())
                .bucket(storageProperties.getBucket())
                .objectKey("pending/" + UUID.randomUUID())
                .uploadedByUserId(callerId)
                .spaceId(purpose.requiresSpace() ? spaceId : null)
                .associated(false)
                .build();
        entity = persistWithCanonicalObjectKey(entity, purpose);
        UUID fileId = entity.getId();
        storageProvider.putStream(
                entity.getBucket(),
                entity.getObjectKey(),
                new ByteArrayInputStream(decoded.bytes()),
                decoded.bytes().length,
                contentType);
        storedFileRepository.save(entity);
        log.info(
                "file_ingested_from_legacy_payload fileId={} purpose={} userId={} size={}",
                fileId,
                purpose,
                callerId,
                decoded.bytes().length);
        return entity;
    }

    @Transactional
    public UUID resolveIncomingFile(
            UUID callerId,
            FilePurpose purpose,
            UUID spaceId,
            UUID fileId,
            String legacyPayload,
            String originalFilename) {
        if (fileId != null) {
            StoredFileEntity file = requireActiveForAssociation(callerId, fileId, purpose, spaceId);
            return file.getId();
        }
        if (FileLegacySupport.isInlinePayload(legacyPayload)) {
            return ingestImage(callerId, purpose, spaceId, legacyPayload, originalFilename).getId();
        }
        return null;
    }

    @Transactional
    public StoredFileEntity requireActiveForAssociation(
            UUID callerId, UUID fileId, FilePurpose purpose, UUID spaceId) {
        StoredFileEntity file = load(fileId);
        fileAuthorizationService.authorizeAssociate(callerId, file, purpose, spaceId);
        return file;
    }

    @Transactional
    public void markAssociated(UUID fileId) {
        StoredFileEntity file = load(fileId);
        file.setAssociated(true);
        storedFileRepository.save(file);
    }

    @Transactional
    public void replaceAssociation(UUID previousFileId, UUID nextFileId) {
        if (previousFileId != null && !previousFileId.equals(nextFileId)) {
            storedFileRepository.findById(previousFileId).ifPresent(this::scheduleDelete);
        }
        if (nextFileId != null) {
            markAssociated(nextFileId);
        }
    }

    @Transactional
    public void scheduleDelete(UUID fileId) {
        storedFileRepository.findById(fileId).ifPresent(this::scheduleDelete);
    }

    public String resolveDisplayUrl(UUID callerId, UUID fileId, String legacyValue) {
        if (fileId != null) {
            try {
                ContentUrlResponse response = createContentUrl(callerId, fileId);
                if (storageProvider.supportsDirectBrowserUpload()) {
                    return response.getContentUrl();
                }
                return response.getContentUrl();
            } catch (RuntimeException ex) {
                log.warn("file_display_url_failed fileId={} userId={}", fileId, callerId);
            }
        }
        if (FileLegacySupport.isMarker(legacyValue)) {
            UUID marked = FileLegacySupport.parseMarker(legacyValue);
            if (marked != null && !marked.equals(fileId)) {
                return resolveDisplayUrl(callerId, marked, null);
            }
            return null;
        }
        if (FileLegacySupport.isDisplayableLegacy(legacyValue)) {
            return legacyValue;
        }
        return null;
    }

    @Transactional
    public int cleanupExpiredPending(LocalDateTime now) {
        int cleaned = 0;
        for (StoredFileEntity file :
                storedFileRepository.findByStatusAndExpiresAtBefore(FileStatus.PENDING, now)) {
            if (file.getStatus() != FileStatus.PENDING) {
                continue;
            }
            deleteObjectQuietly(file);
            file.setStatus(FileStatus.FAILED);
            file.setDeletedAt(now);
            storedFileRepository.save(file);
            cleaned++;
            log.info("file_orphan_pending_cleaned fileId={} purpose={}", file.getId(), file.getPurpose());
        }
        return cleaned;
    }

    @Transactional
    public int markUnassociatedForDelete(LocalDateTime cutoff) {
        int marked = 0;
        for (StoredFileEntity file :
                storedFileRepository.findUnassociatedOlderThan(FileStatus.ACTIVE, cutoff)) {
            if (file.getStatus() != FileStatus.ACTIVE || file.isAssociated()) {
                continue;
            }
            scheduleDelete(file);
            marked++;
            log.info("file_unassociated_marked_delete fileId={} purpose={}", file.getId(), file.getPurpose());
        }
        return marked;
    }

    @Transactional
    public int purgeDue(LocalDateTime now) {
        int purged = 0;
        for (StoredFileEntity file :
                storedFileRepository.findByStatusAndPurgeAfterBefore(FileStatus.PENDING_DELETE, now)) {
            if (file.getStatus() != FileStatus.PENDING_DELETE) {
                continue;
            }
            deleteObjectQuietly(file);
            file.setStatus(FileStatus.DELETED);
            file.setDeletedAt(now);
            storedFileRepository.save(file);
            purged++;
            log.info("file_purged fileId={} purpose={}", file.getId(), file.getPurpose());
        }
        return purged;
    }

    private void scheduleDelete(StoredFileEntity file) {
        if (file.getStatus() == FileStatus.DELETED || file.getStatus() == FileStatus.PENDING_DELETE) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        file.setStatus(FileStatus.PENDING_DELETE);
        file.setDeletedAt(now);
        file.setPurgeAfter(now.plusDays(storageProperties.getPurgeDelayDays()));
        storedFileRepository.save(file);
    }

    private void deleteObjectQuietly(StoredFileEntity file) {
        try {
            storageProvider.delete(file.getBucket(), file.getObjectKey());
        } catch (RuntimeException ex) {
            log.warn("file_object_delete_failed fileId={}", file.getId());
        }
    }

    public StoredFileEntity getRequired(UUID fileId) {
        return load(fileId);
    }

    private StoredFileEntity persistWithCanonicalObjectKey(StoredFileEntity entity, FilePurpose purpose) {
        entity = storedFileRepository.saveAndFlush(entity);
        entity.setObjectKey(ObjectKeyFactory.create(
                FileVisibility.PRIVATE, purpose, entity.getId(), LocalDate.now()));
        return storedFileRepository.save(entity);
    }

    private StoredFileEntity load(UUID fileId) {
        return storedFileRepository
                .findById(fileId)
                .orElseThrow(() -> new BusinessException(
                        FileErrorCodes.FILE_NOT_FOUND, "File not found", HttpStatus.NOT_FOUND));
    }

    private void assertEnabled() {
        if (!storageProperties.isEnabled()) {
            throw new BusinessException(
                    FileErrorCodes.FILE_STORAGE_UNAVAILABLE,
                    "File storage is not available",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private void enforceSessionRateLimit(UUID callerId) {
        long count = storedFileRepository.countCreatedByUserSince(
                callerId, LocalDateTime.now().minusHours(1));
        if (count >= storageProperties.getMaxUploadSessionsPerHour()) {
            throw new RateLimitedException("Too many file uploads. Please try again later.", 3600);
        }
    }

    private String sha256(StoredFileEntity file) {
        try (InputStream in = storageProvider.openStream(file.getBucket(), file.getObjectKey())) {
            return sha256(in.readAllBytes());
        } catch (IOException ex) {
            throw new BusinessException(
                    FileErrorCodes.FILE_STORAGE_ERROR, "Unable to verify file", HttpStatus.BAD_GATEWAY);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }

    private static String normalizeChecksum(String checksum) {
        if (!StringUtils.hasText(checksum)) {
            return null;
        }
        String value = checksum.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-f0-9]{64}")) {
            throw new BusinessException(
                    FileErrorCodes.FILE_TYPE_NOT_ALLOWED, "Invalid checksum", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private static StoredFileResponse toResponse(StoredFileEntity file) {
        return StoredFileResponse.builder()
                .fileId(file.getId())
                .purpose(file.getPurpose())
                .visibility(file.getVisibility())
                .status(file.getStatus())
                .originalFilename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .byteSize(file.getByteSize())
                .checksumSha256(file.getChecksumSha256())
                .spaceId(file.getSpaceId())
                .createdAt(file.getCreatedAt())
                .build();
    }
}
