package com.acomi.acomi_backend.admin.api.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminListingMarketingWhatsAppCapabilityResponse {
    private boolean available;
    private String mode;
    private boolean enabled;
    private boolean senderReady;
    private String businessNumberHint;
    private List<String> reasons;
    private String explanation;
}
