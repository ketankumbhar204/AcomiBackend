package com.acomi.acomi_backend.storage.application.service;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.member.application.service.SpaceMembershipResolver;
import com.acomi.acomi_backend.storage.domain.model.FilePurpose;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owner-only association of a completed stored file to a singular entity photo slot.
 * Storage, signing, and lifecycle stay in {@link StoredFileService}.
 */
@Service
@RequiredArgsConstructor
public class EntityPhotoService {

    private final StoredFileService storedFileService;
    private final SpaceMembershipResolver membershipResolver;

    @Transactional
    public UUID replacePhoto(
            UUID callerId, UUID spaceId, FilePurpose purpose, UUID previousFileId, UUID nextFileId) {
        membershipResolver.requireAccountHolder(spaceId, callerId);
        if (nextFileId == null) {
            throw new BusinessException("fileId is required", HttpStatus.BAD_REQUEST);
        }
        if (nextFileId.equals(previousFileId)) {
            storedFileService.requireActiveForAssociation(callerId, nextFileId, purpose, spaceId);
            return nextFileId;
        }
        UUID resolved =
                storedFileService.requireActiveForAssociation(callerId, nextFileId, purpose, spaceId).getId();
        storedFileService.replaceAssociation(previousFileId, resolved);
        return resolved;
    }

    @Transactional
    public void removePhoto(UUID callerId, UUID spaceId, UUID previousFileId) {
        membershipResolver.requireAccountHolder(spaceId, callerId);
        if (previousFileId != null) {
            storedFileService.replaceAssociation(previousFileId, null);
        }
    }
}
