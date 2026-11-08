package com.acomi.acomi_backend.occupancy.api.dto.request;

import com.acomi.acomi_backend.occupancy.domain.model.AllocationTargetType;
import com.acomi.acomi_backend.occupancy.domain.model.StayKind;
import com.acomi.acomi_backend.space.api.dto.AmenityAssignmentDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Allocate a member to a bed, room, or unit")
public class AllocateOccupancyRequest {

    @NotNull
    private UUID memberId;

    @NotNull
    private AllocationTargetType targetType;

    private UUID bedId;
    private UUID roomId;
    private UUID unitId;

    @Schema(description = "Move-in date for the active allocation. Defaults to today when omitted.")
    private LocalDate moveInDate;

    @Schema(description = "Planned end date. Same meaning as expectedExitDate. If both are sent they must be equal.")
    private LocalDate expectedCheckoutDate;

    @Schema(description = "Planned end date. Same meaning as expectedCheckoutDate. If both are sent they must be equal.")
    private LocalDate expectedExitDate;

    private String remarks;

    @Schema(description = "MONTHLY, SHORT, or OPEN_ENDED. Omitted means a legacy monthly stay.")
    private StayKind stayKind;

    @Schema(
            description =
                    "Legacy fixed short-stay total. For new SHORT stays prefer rentSnapshot (daily rate);"
                            + " server sets agreedTotal = daily × nights when daily rate is sent.")
    private BigDecimal agreedTotal;

    @Schema(
            description =
                    "Monthly rent (MONTHLY), daily rent (OPEN_ENDED / SHORT), or catalog rent."
                            + " For SHORT this is ₹/night; stay rent is daily × nights.")
    private BigDecimal rentSnapshot;

    @Schema(description = "Security deposit contract snapshot; defaults to 0")
    private BigDecimal depositSnapshot;

    private Boolean foodEnabled;
    private BigDecimal foodChargeSnapshot;

    @Schema(description = "When true, food is bundled in rent and no separate food charge is required")
    private Boolean foodIncludedInRent;

    @Valid
    private List<OccupancyChargeLineRequest> otherCharges = new ArrayList<>();

    @Schema(description = "Create FULL meal participation when food is enabled on allocation")
    private boolean createMealParticipation;

    @Valid
    @Schema(description = "Amenities assigned to this occupancy; omit for all space amenities")
    private List<AmenityAssignmentDto> amenities;
}
