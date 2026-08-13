package com.acomi.acomi_backend.occupancy.api.dto.request;

import com.acomi.acomi_backend.space.api.dto.AmenityAssignmentDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Complete move-in for a reserved occupancy")
public class MoveInOccupancyRequest {

    private LocalDate moveInDate;
    private LocalDate expectedExitDate;

    @Schema(description = "Allow move-in before scheduled moveInDate")
    private boolean allowEarlyMoveIn;

    private Boolean agreementSigned;
    private String remarks;

    @Schema(description = "Monthly rent contract snapshot; required if target has no defaultRent")
    private BigDecimal rentSnapshot;

    @Schema(description = "Security deposit contract snapshot; defaults to 0")
    private BigDecimal depositSnapshot;

    private Boolean foodEnabled;
    private BigDecimal foodChargeSnapshot;

    @Schema(description = "Ignored at activation; server copies space foodIncludedInRent policy")
    private Boolean foodIncludedInRent;

    @Valid
    private List<OccupancyChargeLineRequest> otherCharges = new ArrayList<>();

    @Schema(description = "Create FULL meal participation when food is enabled on move-in")
    private boolean createMealParticipation;

    @Valid
    @Schema(description = "Amenities assigned to this occupancy; omit for all space amenities")
    private List<AmenityAssignmentDto> amenities;
}
