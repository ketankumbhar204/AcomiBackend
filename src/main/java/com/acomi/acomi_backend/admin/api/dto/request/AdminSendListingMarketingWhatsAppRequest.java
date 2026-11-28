package com.acomi.acomi_backend.admin.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AdminSendListingMarketingWhatsAppRequest {

    @NotBlank
    @Size(max = 32)
    private String recipientPhone;

    @Size(max = 80)
    private String templateKey;

    @Size(max = 4000)
    private String messageSnapshot;

    /** Client-reported media filenames for audit (files are not accepted on this endpoint yet). */
    private List<@Size(max = 255) String> mediaFileNames = new ArrayList<>();

    /** Optional client idempotency token to prevent double-taps. */
    @Size(max = 80)
    private String clientRequestId;
}
