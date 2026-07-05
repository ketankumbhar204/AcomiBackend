package com.countin.countin_backend.occupancy.api.dto.request;

import com.countin.countin_backend.occupancy.domain.model.AllocationTargetType;
import com.countin.countin_backend.space.api.dto.AmenityAssignmentDto;
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

    private LocalDate expectedCheckoutDate;
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

    @Schema(description = "Create FULL meal participation when food is enabled on allocation")
    private boolean createMealParticipation;

    @Valid
    @Schema(description = "Amenities assigned to this occupancy; omit for all space amenities")
    private List<AmenityAssignmentDto> amenities;
}
