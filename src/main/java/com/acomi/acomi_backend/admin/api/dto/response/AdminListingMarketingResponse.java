package com.acomi.acomi_backend.admin.api.dto.response;

import com.acomi.acomi_backend.admin.domain.model.ListingMarketingStatus;
import com.acomi.acomi_backend.admin.domain.model.WhatsAppAvailability;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminListingMarketingResponse {
    private ListingMarketingStatus marketingStatus;
    private WhatsAppAvailability whatsappAvailability;
    private LocalDateTime lastSharedAt;
    private UUID lastSharedBy;
    private String marketingNotes;
    private UUID propertyRegistrationId;
    private UUID messRegistrationId;
    private UUID spaceId;
    private List<AdminListingMarketingActivityResponse> activities;
}
