package com.acomi.acomi_backend.admin.api.dto.request;

import com.acomi.acomi_backend.admin.domain.model.ListingMarketingChannel;
import com.acomi.acomi_backend.admin.domain.model.ListingMarketingOutcome;
import com.acomi.acomi_backend.admin.domain.model.ListingMarketingStatus;
import com.acomi.acomi_backend.admin.domain.model.WhatsAppAvailability;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AdminUpdateListingMarketingRequest {

    private ListingMarketingStatus marketingStatus;
    private WhatsAppAvailability whatsappAvailability;

    @Size(max = 2000)
    private String marketingNotes;

    @Valid
    private Activity activity;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Activity {
        private ListingMarketingChannel channel;
        private ListingMarketingOutcome outcome;

        @Size(max = 80)
        private String templateKey;

        @Size(max = 2000)
        private String note;

        @Size(max = 4000)
        private String messageSnapshot;
    }
}
