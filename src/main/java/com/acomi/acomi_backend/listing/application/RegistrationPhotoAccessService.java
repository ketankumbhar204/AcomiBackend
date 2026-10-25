package com.acomi.acomi_backend.listing.application;

import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.mess.infrastructure.persistence.entity.MessRegistrationEntity;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.acomi.acomi_backend.registration.infrastructure.persistence.entity.RegistrationPhotoEntity;
import com.acomi.acomi_backend.registration.infrastructure.persistence.repository.RegistrationPhotoRepository;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegistrationPhotoAccessService {

    private final RegistrationPhotoRepository registrationPhotoRepository;
    private final PropertyRegistrationRepository propertyRegistrationRepository;
    private final MessRegistrationRepository messRegistrationRepository;
    private final SpaceRepository spaceRepository;
    private final SpaceMembershipRepository spaceMembershipRepository;

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> content(UUID photoId) {
        RegistrationPhotoEntity photo = registrationPhotoRepository
                .findById(photoId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration photo", "id", photoId));
        if (!mayRead(photo)) {
            throw new ResourceNotFoundException("Registration photo", "id", photoId);
        }
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            mediaType = MediaType.parseMediaType(photo.getContentType());
        } catch (Exception ignored) {
            // Keep the generic type when the stored value is not a valid media type.
        }
        return ResponseEntity.ok().contentType(mediaType).body(photo.getContent());
    }

    private boolean mayRead(RegistrationPhotoEntity photo) {
        UUID callerId = SecurityUtils.getCurrentUserIdOrNull();
        if (photo.getPropertyRegistrationId() != null) {
            PropertyRegistrationEntity lead = propertyRegistrationRepository
                    .findById(photo.getPropertyRegistrationId())
                    .orElse(null);
            return lead != null && allowed(callerId, lead.getLinkedOwnerUserId(), lead.getConvertedSpaceId());
        }
        if (photo.getMessRegistrationId() != null) {
            MessRegistrationEntity lead = messRegistrationRepository
                    .findById(photo.getMessRegistrationId())
                    .orElse(null);
            return lead != null && allowed(callerId, lead.getLinkedOwnerUserId(), lead.getConvertedSpaceId());
        }
        return false;
    }

    private boolean allowed(UUID callerId, UUID linkedOwnerId, UUID spaceId) {
        if (callerId != null && callerId.equals(linkedOwnerId)) {
            return true;
        }
        if (spaceId == null) {
            return false;
        }
        SpaceEntity space = spaceRepository.findById(spaceId).orElse(null);
        if (space == null || !space.isActive()) {
            return false;
        }
        if (callerId != null
                && spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                        callerId, spaceId, List.of(MembershipRole.OWNER, MembershipRole.MANAGER))) {
            return true;
        }
        return space.isDiscoverable();
    }
}
