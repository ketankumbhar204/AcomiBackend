package com.acomi.acomi_backend.admin.api.dto.response;

import com.acomi.acomi_backend.admin.domain.model.ListingMarketingOutcome;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminSendListingMarketingWhatsAppResponse {
    private boolean accepted;
    private ListingMarketingOutcome outcome;
    private UUID activityId;
    private String providerMessageId;
    private String explanation;
    private AdminListingMarketingResponse marketing;
}
