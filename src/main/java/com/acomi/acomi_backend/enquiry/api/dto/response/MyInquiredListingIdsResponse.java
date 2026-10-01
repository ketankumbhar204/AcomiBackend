package com.acomi.acomi_backend.enquiry.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Listing ids the current user has already enquired about. No contact or enquiry body.")
public class MyInquiredListingIdsResponse {

    @Schema(description = "Space ids with an unexpired PENDING or SHARED enquiry for the caller")
    private List<UUID> inquiredListingIds;

    @Schema(description = "Same listings, with EMAIL, APP, or BOTH. No address or phone number.")
    private List<InquiredListingDestinationResponse> inquiries;
}
