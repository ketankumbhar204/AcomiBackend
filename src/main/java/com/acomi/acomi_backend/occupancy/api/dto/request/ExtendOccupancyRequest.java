package com.acomi.acomi_backend.occupancy.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(
        description =
                "Extend planned end and/or revise rent. For OPEN_ENDED daily-rate revision only, "
                        + "omit expectedExitDate and send rentSnapshot + rentEffectiveMonth (effective-from date).")
public class ExtendOccupancyRequest {

    /**
     * Required for MONTHLY/SHORT extensions and OPEN_ENDED planned-end changes.
     * Optional when OPEN_ENDED revises daily rent only (stayKind unchanged, end unchanged).
     */
    private LocalDate expectedExitDate;

    private BigDecimal additionalAmount;

    @NotBlank
    @Size(max = 64)
    private String idempotencyKey;

    private BigDecimal rentSnapshot;

    /**
     * Rent change effective date. MONTHLY: normalized to the 1st of the month.
     * OPEN_ENDED daily rate: stored as the calendar day the new rate begins.
     */
    private LocalDate rentEffectiveMonth;

    /** Optional owner-facing reason for an OPEN_ENDED rent revision (stored on history remarks). */
    @Size(max = 240)
    private String reason;
}
