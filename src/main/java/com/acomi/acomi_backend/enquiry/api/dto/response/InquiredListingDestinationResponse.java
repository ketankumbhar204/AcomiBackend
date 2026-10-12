package com.acomi.acomi_backend.enquiry.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Where the current user's enquiry for one listing was sent. Includes only that user's own destination email, never owner contact.")
public class InquiredListingDestinationResponse {

    private UUID listingId;

    @Schema(description = "Enquiry id when this row is only a revealed website contact.")
    private UUID enquiryId;

    @Schema(description = "True when this user has already revealed owner contact on the website for this listing.")
    private Boolean contactViewed;

    @Schema(description = "EMAIL, APP, or BOTH", allowableValues = {"EMAIL", "APP", "BOTH"})
    private String sentVia;

    @Schema(description = "Email address this user asked ACOMI to send the enquiry to. Null for app-only delivery.")
    private String sentToEmail;

    @Schema(description = "When the enquiry was sent, so the user can find it in their inbox.")
    private LocalDateTime sentAt;
}
