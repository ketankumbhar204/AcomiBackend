package com.acomi.acomi_backend.enquiry.api.dto.response;

import com.acomi.acomi_backend.enquiry.domain.model.EnquiryRequesterType;
import com.acomi.acomi_backend.enquiry.domain.model.SpaceEnquiryStatus;
import com.acomi.acomi_backend.enquiry.infrastructure.persistence.entity.SpaceEnquiryEntity;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminSpaceEnquiryDetailResponse {

    private UUID enquiryId;
    private UUID spaceId;
    private String spaceName;
    private SpaceType spaceType;
    private String spaceAddress;
    private String locationLabel;
    private String capacityLabel;
    private UUID requesterUserId;
    private String requesterName;
    private String requesterEmail;
    private String requesterMobile;
    private EnquiryRequesterType requesterType;
    private SpaceEnquiryStatus status;
    private LocalDateTime requestedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime reviewedAt;
    private LocalDateTime sharedAt;
    private UUID sharedByAdminId;
    private boolean automaticallyShared;
    private LocalDateTime rejectedAt;
    private String rejectionReason;
    private OwnerContactResponse ownerContact;
    /** True when the listing was converted from a test property/mess lead. */
    private boolean testLead;

    public static AdminSpaceEnquiryDetailResponse from(
            SpaceEnquiryEntity entity,
            SpaceType spaceType,
            String spaceAddress,
            OwnerContactResponse ownerContact) {
        return from(entity, spaceType, spaceAddress, null, null, null, ownerContact, false);
    }

    public static AdminSpaceEnquiryDetailResponse from(
            SpaceEnquiryEntity entity,
            SpaceType spaceType,
            String spaceAddress,
            String locationLabel,
            String capacityLabel,
            String requesterMobile,
            OwnerContactResponse ownerContact) {
        return from(
                entity, spaceType, spaceAddress, locationLabel, capacityLabel, requesterMobile, ownerContact, false);
    }

    public static AdminSpaceEnquiryDetailResponse from(
            SpaceEnquiryEntity entity,
            SpaceType spaceType,
            String spaceAddress,
            String locationLabel,
            String capacityLabel,
            String requesterMobile,
            OwnerContactResponse ownerContact,
            boolean testLead) {
        return AdminSpaceEnquiryDetailResponse.builder()
                .enquiryId(entity.getId())
                .spaceId(entity.getSpaceId())
                .spaceName(entity.getSpaceNameSnapshot())
                .spaceType(spaceType)
                .spaceAddress(spaceAddress)
                .locationLabel(locationLabel)
                .capacityLabel(capacityLabel)
                .requesterUserId(entity.getRequesterUserId())
                .requesterName(entity.getRequesterNameSnapshot())
                .requesterEmail(entity.getRequesterEmail())
                .requesterMobile(requesterMobile)
                .requesterType(entity.getRequesterType())
                .status(entity.getStatus())
                .requestedAt(entity.getRequestedAt())
                .expiresAt(entity.getExpiresAt())
                .reviewedAt(entity.getReviewedAt())
                .sharedAt(entity.getSharedAt())
                .sharedByAdminId(entity.getSharedByAdminId())
                .automaticallyShared(
                        entity.getStatus() == SpaceEnquiryStatus.SHARED && entity.getSharedByAdminId() == null)
                .rejectedAt(entity.getRejectedAt())
                .rejectionReason(entity.getRejectionReason())
                .ownerContact(ownerContact)
                .testLead(testLead)
                .build();
    }
}
