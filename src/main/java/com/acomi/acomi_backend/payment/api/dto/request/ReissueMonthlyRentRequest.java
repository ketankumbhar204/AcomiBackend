package com.acomi.acomi_backend.payment.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Explicit reissue of a cancelled or fully refunded monthly rent row")
public class ReissueMonthlyRentRequest {

    @NotBlank
    @Size(max = 500)
    @Schema(description = "Why this closed monthly rent obligation is being issued again")
    private String reason;
}
