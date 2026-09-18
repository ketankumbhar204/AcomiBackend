package com.acomi.acomi_backend.storage.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.member.application.service.SpaceMembershipResolver;
import com.acomi.acomi_backend.storage.domain.model.FilePurpose;
import com.acomi.acomi_backend.storage.domain.model.FileStatus;
import com.acomi.acomi_backend.storage.domain.model.FileVisibility;
import com.acomi.acomi_backend.storage.infrastructure.persistence.entity.StoredFileEntity;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class EntityPhotoServiceTest {

    @Mock
    private StoredFileService storedFileService;

    @Mock
    private SpaceMembershipResolver membershipResolver;

    @InjectMocks
    private EntityPhotoService entityPhotoService;

    @Test
    void ownerReplaceAssociatesAndUnlinksPrevious() {
        UUID callerId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID previous = UUID.randomUUID();
        UUID next = UUID.randomUUID();
        StoredFileEntity file = StoredFileEntity.builder()
                .purpose(FilePurpose.BUILDING_PHOTO)
                .visibility(FileVisibility.PRIVATE)
                .status(FileStatus.ACTIVE)
                .contentType("image/jpeg")
                .storageProvider("memory")
                .bucket("b")
                .objectKey("k")
                .uploadedByUserId(callerId)
                .spaceId(spaceId)
                .build();
        file.setId(next);
        when(storedFileService.requireActiveForAssociation(
                        callerId, next, FilePurpose.BUILDING_PHOTO, spaceId))
                .thenReturn(file);

        UUID result = entityPhotoService.replacePhoto(
                callerId, spaceId, FilePurpose.BUILDING_PHOTO, previous, next);

        assertThat(result).isEqualTo(next);
        verify(membershipResolver).requireAccountHolder(spaceId, callerId);
        verify(storedFileService).replaceAssociation(previous, next);
    }

    @Test
    void sameFileIdDoesNotCreateDuplicateAssociation() {
        UUID callerId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        StoredFileEntity file = StoredFileEntity.builder()
                .purpose(FilePurpose.ROOM_PHOTO)
                .visibility(FileVisibility.PRIVATE)
                .status(FileStatus.ACTIVE)
                .contentType("image/jpeg")
                .storageProvider("memory")
                .bucket("b")
                .objectKey("k")
                .uploadedByUserId(callerId)
                .spaceId(spaceId)
                .build();
        file.setId(fileId);
        when(storedFileService.requireActiveForAssociation(
                        callerId, fileId, FilePurpose.ROOM_PHOTO, spaceId))
                .thenReturn(file);

        UUID result = entityPhotoService.replacePhoto(
                callerId, spaceId, FilePurpose.ROOM_PHOTO, fileId, fileId);

        assertThat(result).isEqualTo(fileId);
        verify(storedFileService, never()).replaceAssociation(any(), any());
    }

    @Test
    void nonOwnerReplaceIsRejected() {
        UUID callerId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        doThrow(new BusinessException(
                        "ACCOUNT_HOLDER_REQUIRED",
                        "Only the account holder can edit this photo",
                        HttpStatus.FORBIDDEN))
                .when(membershipResolver)
                .requireAccountHolder(spaceId, callerId);

        assertThatThrownBy(() -> entityPhotoService.replacePhoto(
                        callerId, spaceId, FilePurpose.COMBO_PHOTO, null, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("ACCOUNT_HOLDER_REQUIRED");
        verify(storedFileService, never()).requireActiveForAssociation(any(), any(), any(), any());
        verify(storedFileService, never()).replaceAssociation(any(), any());
    }

    @Test
    void ownerRemoveUnlinksPrevious() {
        UUID callerId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID previous = UUID.randomUUID();

        entityPhotoService.removePhoto(callerId, spaceId, previous);

        verify(membershipResolver).requireAccountHolder(spaceId, callerId);
        verify(storedFileService).replaceAssociation(previous, null);
    }

    @Test
    void nonOwnerRemoveIsRejected() {
        UUID callerId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        doThrow(new BusinessException(
                        "ACCOUNT_HOLDER_REQUIRED",
                        "Only the account holder can edit this photo",
                        HttpStatus.FORBIDDEN))
                .when(membershipResolver)
                .requireAccountHolder(spaceId, callerId);

        assertThatThrownBy(() -> entityPhotoService.removePhoto(callerId, spaceId, UUID.randomUUID()))
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("ACCOUNT_HOLDER_REQUIRED");
        verify(storedFileService, never()).replaceAssociation(any(), any());
    }
}
