package com.acomi.acomi_backend.enquiry.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Where an existing enquiry for one listing was sent. No email address or phone number.")
public class InquiredListingDestinationResponse {

    private UUID listingId;

    @Schema(description = "EMAIL, APP, or BOTH", allowableValues = {"EMAIL", "APP", "BOTH"})
    private String sentVia;
}
