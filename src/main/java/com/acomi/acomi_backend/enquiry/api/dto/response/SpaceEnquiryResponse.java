package com.acomi.acomi_backend.enquiry.api.dto.response;

import com.acomi.acomi_backend.enquiry.domain.model.EnquiryRequesterType;
import com.acomi.acomi_backend.enquiry.domain.model.SpaceEnquiryStatus;
import com.acomi.acomi_backend.enquiry.infrastructure.persistence.entity.SpaceEnquiryEntity;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/** Member/owner facing enquiry record. Never includes owner contact. */
@Getter
@Builder(toBuilder = true)
public class SpaceEnquiryResponse {

    private UUID enquiryId;
    private UUID spaceId;
    private String spaceName;
    private SpaceType spaceType;
    private String locationLabel;
    private String sharingNotes;
    private List<String> amenityLabels;
    private Boolean foodIncludedInRent;
    private EnquiryRequesterType requesterType;
    private SpaceEnquiryStatus status;
    private LocalDateTime requestedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime sharedAt;
    private boolean detailsShared;
    private String requesterEmail;
    /** True when create reused an open enquiry and did not send a new customer email. */
    @Builder.Default
    private boolean reusedExisting = false;

    public static SpaceEnquiryResponse from(SpaceEnquiryEntity entity) {
        return from(entity, null, null, null, null, null);
    }

    public static SpaceEnquiryResponse from(
            SpaceEnquiryEntity entity,
            SpaceType spaceType,
            String locationLabel,
            String sharingNotes,
            List<String> amenityLabels,
            Boolean foodIncludedInRent) {
        return SpaceEnquiryResponse.builder()
                .enquiryId(entity.getId())
                .spaceId(entity.getSpaceId())
                .spaceName(entity.getSpaceNameSnapshot())
                .spaceType(spaceType)
                .locationLabel(locationLabel)
                .sharingNotes(sharingNotes)
                .amenityLabels(amenityLabels == null ? List.of() : amenityLabels)
                .foodIncludedInRent(foodIncludedInRent)
                .requesterType(entity.getRequesterType())
                .status(entity.getStatus())
                .requestedAt(entity.getRequestedAt())
                .expiresAt(entity.getExpiresAt())
                .sharedAt(entity.getSharedAt())
                .detailsShared(entity.getStatus() == SpaceEnquiryStatus.SHARED)
                .requesterEmail(entity.getRequesterEmail())
                .reusedExisting(false)
                .build();
    }
}
