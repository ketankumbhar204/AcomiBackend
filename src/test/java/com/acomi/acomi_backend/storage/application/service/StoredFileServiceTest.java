package com.acomi.acomi_backend.storage.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.storage.api.dto.request.CreateUploadSessionRequest;
import com.acomi.acomi_backend.storage.api.dto.response.StoredFileResponse;
import com.acomi.acomi_backend.storage.api.dto.response.UploadSessionResponse;
import com.acomi.acomi_backend.storage.application.support.FileValidationService;
import com.acomi.acomi_backend.storage.config.StorageProperties;
import com.acomi.acomi_backend.storage.domain.model.FileErrorCodes;
import com.acomi.acomi_backend.storage.domain.model.FilePurpose;
import com.acomi.acomi_backend.storage.domain.model.FileStatus;
import com.acomi.acomi_backend.storage.infrastructure.persistence.entity.StoredFileEntity;
import com.acomi.acomi_backend.storage.infrastructure.persistence.repository.StoredFileRepository;
import com.acomi.acomi_backend.storage.infrastructure.provider.memory.InMemoryStorageProvider;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoredFileServiceTest {

    @Mock
    private StoredFileRepository storedFileRepository;

    @Mock
    private FileAuthorizationService fileAuthorizationService;

    private final Map<UUID, StoredFileEntity> store = new ConcurrentHashMap<>();
    private InMemoryStorageProvider storageProvider;
    private StoredFileService service;
    private UUID callerId;

    @BeforeEach
    void setUp() {
        callerId = UUID.randomUUID();
        storageProvider = new InMemoryStorageProvider();
        StorageProperties properties = new StorageProperties();
        properties.setEnabled(true);
        properties.setProvider("memory");
        properties.setLogicalName("memory");
        properties.setBucket("test-bucket");
        service = new StoredFileService(
                storedFileRepository,
                storageProvider,
                properties,
                fileAuthorizationService,
                new FileValidationService());

        lenient().when(storedFileRepository.save(any(StoredFileEntity.class))).thenAnswer(invocation -> {
            StoredFileEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            store.put(entity.getId(), entity);
            return entity;
        });
        lenient().when(storedFileRepository.saveAndFlush(any(StoredFileEntity.class))).thenAnswer(invocation -> {
            StoredFileEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            store.put(entity.getId(), entity);
            return entity;
        });
        lenient().when(storedFileRepository.findById(any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(store.get(invocation.getArgument(0))));
        lenient().when(storedFileRepository.countCreatedByUserSince(any(), any())).thenReturn(0L);
        lenient().doNothing().when(fileAuthorizationService).authorizeCreateSession(any(), any());
        lenient().doNothing().when(fileAuthorizationService).authorizeComplete(any(), any());
        lenient().doNothing().when(fileAuthorizationService).authorizeRead(any(), any());
        lenient().doNothing().when(fileAuthorizationService).authorizeDelete(any(), any());
        lenient().doNothing().when(fileAuthorizationService).authorizeAssociate(any(), any(), any(), any());
    }

    @Test
    void createSessionCompleteAndDownloadAreIdempotent() {
        byte[] jpeg = jpegBytes(64);
        CreateUploadSessionRequest request = sessionRequest(FilePurpose.PROFILE_PHOTO, jpeg.length);
        UploadSessionResponse session = service.createUploadSession(callerId, request);
        assertThat(session.getFileId()).isNotNull();
        assertThat(session.isUseAcomiUploadProxy()).isTrue();
        assertThat(session.getUploadUrl()).startsWith("/files/");

        StoredFileEntity pending = store.get(session.getFileId());
        assertThat(pending.getObjectKey()).endsWith("/" + session.getFileId());
        assertThat(pending.getObjectKey()).startsWith("private/profile-photo/");
        storageProvider.putStream(
                pending.getBucket(),
                pending.getObjectKey(),
                new ByteArrayInputStream(jpeg),
                jpeg.length,
                "image/jpeg");

        StoredFileResponse first = service.complete(callerId, session.getFileId());
        StoredFileResponse second = service.complete(callerId, session.getFileId());
        assertThat(first.getStatus()).isEqualTo(FileStatus.ACTIVE);
        assertThat(second.getFileId()).isEqualTo(first.getFileId());
        assertThat(service.createContentUrl(callerId, session.getFileId()).getContentUrl())
                .isEqualTo("/files/" + session.getFileId() + "/content");
    }

    @Test
    void completeFailsWhenObjectIsMissing() {
        CreateUploadSessionRequest request = sessionRequest(FilePurpose.PROFILE_PHOTO, 32);
        UploadSessionResponse session = service.createUploadSession(callerId, request);
        assertThatThrownBy(() -> service.complete(callerId, session.getFileId()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(FileErrorCodes.UPLOAD_NOT_COMPLETED);
        assertThat(store.get(session.getFileId()).getStatus()).isEqualTo(FileStatus.PENDING);
    }

    @Test
    void completeFailsOnSizeMismatch() {
        byte[] jpeg = jpegBytes(40);
        CreateUploadSessionRequest request = sessionRequest(FilePurpose.PROFILE_PHOTO, 80);
        UploadSessionResponse session = service.createUploadSession(callerId, request);
        StoredFileEntity pending = store.get(session.getFileId());
        storageProvider.putStream(
                pending.getBucket(),
                pending.getObjectKey(),
                new ByteArrayInputStream(jpeg),
                jpeg.length,
                "image/jpeg");
        assertThatThrownBy(() -> service.complete(callerId, session.getFileId()))
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(FileErrorCodes.FILE_TOO_LARGE);
    }

    @Test
    void rejectsInvalidTypeOnSession() {
        CreateUploadSessionRequest request = sessionRequest(FilePurpose.PROFILE_PHOTO, 32);
        request.setContentType("text/html");
        assertThatThrownBy(() -> service.createUploadSession(callerId, request))
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(FileErrorCodes.FILE_TYPE_NOT_ALLOWED);
    }

    @Test
    void ingestDoesNotWriteBase64AndDeleteIsSoft() {
        byte[] jpeg = jpegBytes(48);
        String dataUri = "data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(jpeg);
        StoredFileEntity ingested =
                service.ingestImage(callerId, FilePurpose.PROFILE_PHOTO, null, dataUri, "photo.jpg");
        assertThat(ingested.getStatus()).isEqualTo(FileStatus.ACTIVE);
        assertThat(ingested.getObjectKey()).contains("profile-photo");

        service.delete(callerId, ingested.getId());
        assertThat(store.get(ingested.getId()).getStatus()).isEqualTo(FileStatus.PENDING_DELETE);
        assertThat(storageProvider.head(ingested.getBucket(), ingested.getObjectKey()).exists()).isTrue();
    }

    @Test
    void cleanupExpiresPendingAndPurgesDeleted() {
        byte[] jpeg = jpegBytes(32);
        CreateUploadSessionRequest request = sessionRequest(FilePurpose.PROFILE_PHOTO, jpeg.length);
        UploadSessionResponse session = service.createUploadSession(callerId, request);
        StoredFileEntity pending = store.get(session.getFileId());
        pending.setExpiresAt(LocalDateTime.now().minusMinutes(20));
        storageProvider.putStream(
                pending.getBucket(),
                pending.getObjectKey(),
                new ByteArrayInputStream(jpeg),
                jpeg.length,
                "image/jpeg");
        when(storedFileRepository.findByStatusAndExpiresAtBefore(any(), any())).thenReturn(java.util.List.of(pending));

        assertThat(service.cleanupExpiredPending(LocalDateTime.now())).isEqualTo(1);
        assertThat(pending.getStatus()).isEqualTo(FileStatus.FAILED);
        assertThat(storageProvider.head(pending.getBucket(), pending.getObjectKey()).exists()).isFalse();

        pending.setStatus(FileStatus.PENDING_DELETE);
        pending.setPurgeAfter(LocalDateTime.now().minusDays(1));
        storageProvider.putStream(
                pending.getBucket(),
                pending.getObjectKey(),
                new ByteArrayInputStream(jpeg),
                jpeg.length,
                "image/jpeg");
        when(storedFileRepository.findByStatusAndPurgeAfterBefore(any(), any()))
                .thenReturn(java.util.List.of(pending));
        assertThat(service.purgeDue(LocalDateTime.now())).isEqualTo(1);
        assertThat(pending.getStatus()).isEqualTo(FileStatus.DELETED);
        assertThat(service.purgeDue(LocalDateTime.now())).isEqualTo(0);
    }

    @Test
    void resolveIncomingFileIdAndLegacyHttpAreNotDestroyed() {
        UUID existing = UUID.randomUUID();
        StoredFileEntity file = StoredFileEntity.builder()
                .purpose(FilePurpose.PAYMENT_PROOF)
                .visibility(com.acomi.acomi_backend.storage.domain.model.FileVisibility.PRIVATE)
                .status(FileStatus.ACTIVE)
                .contentType("image/jpeg")
                .byteSize(32L)
                .storageProvider("memory")
                .bucket("test-bucket")
                .objectKey("private/payment-proof/2026/09/" + existing)
                .uploadedByUserId(callerId)
                .associated(true)
                .build();
        file.setId(existing);
        store.put(existing, file);

        UUID resolved = service.resolveIncomingFile(
                callerId, FilePurpose.PAYMENT_PROOF, UUID.randomUUID(), existing, "https://old.example/p.jpg", "x");
        assertThat(resolved).isEqualTo(existing);
        assertThat(service.resolveIncomingFile(
                        callerId, FilePurpose.PAYMENT_PROOF, UUID.randomUUID(), null, "https://old.example/p.jpg", "x"))
                .isNull();
    }

    private static CreateUploadSessionRequest sessionRequest(FilePurpose purpose, int size) {
        CreateUploadSessionRequest request = new CreateUploadSessionRequest();
        request.setPurpose(purpose);
        request.setContentType("image/jpeg");
        request.setByteSize((long) size);
        request.setOriginalFilename("photo.jpg");
        return request;
    }

    private static byte[] jpegBytes(int length) {
        byte[] bytes = new byte[Math.max(length, 16)];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        return bytes;
    }
}
