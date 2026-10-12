package com.acomi.acomi_backend.enquiry.api.dto.response;

import com.acomi.acomi_backend.enquiry.domain.model.SpaceEnquiryStatus;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/** An enquiry a listing owner or manager is allowed to see. No owner-contact payload. */
@Getter
@Builder
public class ReceivedEnquiryResponse {
    private UUID enquiryId;
    private UUID spaceId;
    private String spaceName;
    private SpaceType spaceType;
    private String locationLabel;
    private String requesterName;
    private String requesterEmail;
    private String requesterMobile;
    private SpaceEnquiryStatus status;
    private LocalDateTime requestedAt;
    private LocalDateTime expiresAt;
}
