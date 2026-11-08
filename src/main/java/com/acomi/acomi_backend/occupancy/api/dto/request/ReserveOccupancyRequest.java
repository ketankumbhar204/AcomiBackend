package com.acomi.acomi_backend.occupancy.api.dto.request;

import com.acomi.acomi_backend.member.domain.model.MemberCategory;
import com.acomi.acomi_backend.occupancy.domain.model.AllocationTargetType;
import com.acomi.acomi_backend.occupancy.domain.model.StayKind;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Reserve a member on a bed, room, or unit for a future move-in")
public class ReserveOccupancyRequest {

    @NotNull
    private UUID memberId;

    @NotNull
    private AllocationTargetType targetType;

    private UUID bedId;
    private UUID roomId;
    private UUID unitId;

    @NotNull
    private LocalDate moveInDate;

    private LocalDate expectedExitDate;

    @Schema(description = "MONTHLY, SHORT, or OPEN_ENDED. Omitted means a legacy monthly stay.")
    private StayKind stayKind;

    @Schema(description = "Owner-entered short-stay total. Required for SHORT. Not a nightly rate.")
    private BigDecimal agreedTotal;

    @Schema(description = "Monthly rent (MONTHLY) or daily rent (OPEN_ENDED / DAILY_RATE). For MONTHLY with booking advance, this is the first-period rent commitment.")
    private BigDecimal rentSnapshot;

    @Schema(
            description =
                    "Optional booking advance — a portion of the first-period agreed rent (MONTHLY/SHORT only). "
                            + "Not an additional charge. When > 0, creates RENT/ADVANCE + remaining RENT obligations at reservation.")
    private BigDecimal bookingAdvanceAmount;

    @Schema(description = "When true and bookingAdvanceAmount > 0, the advance obligation is created as PAID.")
    private Boolean bookingAdvanceCollected;

    @Schema(description = "Payment method when bookingAdvanceCollected is true.")
    private SpacePaymentMethod bookingAdvancePaymentMethod;

    @Schema(description = "Optional security deposit snapshot for the reservation.")
    private BigDecimal depositSnapshot;

    private Boolean foodEnabled;
    private Boolean foodIncludedInRent;
    private BigDecimal foodChargeSnapshot;

    private MemberCategory memberCategory;
    private String remarks;
}
