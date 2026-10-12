package com.acomi.acomi_backend.enquiry.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Whether the owner-contact email for one enquiry has been accepted by the mail transport.")
public class EnquiryEmailStatusResponse {

    private UUID enquiryId;

    @Schema(description = "SENDING, SENT, FAILED, or NONE", allowableValues = {"SENDING", "SENT", "FAILED", "NONE"})
    private String status;

    private String recipientEmail;

    private LocalDateTime sentAt;
}
