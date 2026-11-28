package com.acomi.acomi_backend.admin.api.dto.response;

import com.acomi.acomi_backend.admin.domain.model.ListingMarketingChannel;
import com.acomi.acomi_backend.admin.domain.model.ListingMarketingDeliveryMode;
import com.acomi.acomi_backend.admin.domain.model.ListingMarketingKind;
import com.acomi.acomi_backend.admin.domain.model.ListingMarketingOutcome;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminListingMarketingActivityResponse {
    private UUID id;
    private ListingMarketingKind listingKind;
    private UUID propertyRegistrationId;
    private UUID messRegistrationId;
    private UUID spaceId;
    private ListingMarketingChannel channel;
    private ListingMarketingOutcome outcome;
    private String templateKey;
    private String note;
    private ListingMarketingDeliveryMode deliveryMode;
    private String providerMessageId;
    private String recipientPhoneMasked;
    private UUID actorUserId;
    private LocalDateTime createdAt;
}
