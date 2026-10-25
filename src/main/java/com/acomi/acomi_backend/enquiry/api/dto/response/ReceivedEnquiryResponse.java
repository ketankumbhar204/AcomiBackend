package com.acomi.acomi_backend.enquiry.api.dto.response;

import com.acomi.acomi_backend.enquiry.domain.model.EnquiryRequesterType;
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
    private String referenceCode;
    private UUID spaceId;
    private String spaceName;
    private SpaceType spaceType;
    private String locationLabel;
    private String sharingNotes;
    private String foodIncludedLabel;
    /** False when the listing was deleted or deactivated after the enquiry was sent. */
    private boolean listingAvailable;
    private String requesterName;
    private String requesterEmail;
    private String requesterMobile;
    /** MEMBER or OWNER at the time the enquiry was sent. */
    private EnquiryRequesterType requesterType;
    private SpaceEnquiryStatus status;
    private LocalDateTime requestedAt;
    private LocalDateTime expiresAt;
}
