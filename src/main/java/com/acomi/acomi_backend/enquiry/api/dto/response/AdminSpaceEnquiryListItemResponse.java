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
public class AdminSpaceEnquiryListItemResponse {

    private UUID enquiryId;
    private UUID spaceId;
    private String spaceName;
    private SpaceType spaceType;
    private String spaceAddress;
    private String locationLabel;
    private UUID requesterUserId;
    private String requesterName;
    private String requesterEmail;
    private String requesterMobile;
    private EnquiryRequesterType requesterType;
    private SpaceEnquiryStatus status;
    private LocalDateTime requestedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime sharedAt;
    /** True when the listing was converted from a test property/mess lead. */
    private boolean testLead;

    public static AdminSpaceEnquiryListItemResponse from(
            SpaceEnquiryEntity entity,
            SpaceType spaceType,
            String spaceAddress,
            String locationLabel,
            String requesterMobile) {
        return from(entity, spaceType, spaceAddress, locationLabel, requesterMobile, false);
    }

    public static AdminSpaceEnquiryListItemResponse from(
            SpaceEnquiryEntity entity,
            SpaceType spaceType,
            String spaceAddress,
            String locationLabel,
            String requesterMobile,
            boolean testLead) {
        return AdminSpaceEnquiryListItemResponse.builder()
                .enquiryId(entity.getId())
                .spaceId(entity.getSpaceId())
                .spaceName(entity.getSpaceNameSnapshot())
                .spaceType(spaceType)
                .spaceAddress(spaceAddress)
                .locationLabel(locationLabel)
                .requesterUserId(entity.getRequesterUserId())
                .requesterName(entity.getRequesterNameSnapshot())
                .requesterEmail(entity.getRequesterEmail())
                .requesterMobile(requesterMobile)
                .requesterType(entity.getRequesterType())
                .status(entity.getStatus())
                .requestedAt(entity.getRequestedAt())
                .expiresAt(entity.getExpiresAt())
                .sharedAt(entity.getSharedAt())
                .testLead(testLead)
                .build();
    }

    /** Backward-compatible mapping used by older call sites. */
    public static AdminSpaceEnquiryListItemResponse from(SpaceEnquiryEntity entity) {
        return from(entity, null, null, null, null, false);
    }
}
