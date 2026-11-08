package com.acomi.acomi_backend.occupancy.api.dto.request;

import com.acomi.acomi_backend.occupancy.domain.model.AllocationTargetType;
import com.acomi.acomi_backend.occupancy.domain.model.StayKind;
import com.acomi.acomi_backend.occupancy.domain.model.TransferRentPolicy;
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
@Schema(description = "Transfer an active occupancy to a new target")
public class TransferOccupancyRequest {

    @NotNull
    private AllocationTargetType targetType;

    private UUID bedId;
    private UUID roomId;
    private UUID unitId;

    private String remarks;

    @Schema(
            description =
                    "Destination stay kind. When omitted, the source occupancy stay kind is copied "
                            + "(legacy clients). When set, destination uses this kind with agreedTotal / "
                            + "expectedExitDate as required for SHORT.")
    private StayKind stayKind;

    @Schema(description = "Required when stayKind is SHORT: fixed agreed total for the destination stay")
    private BigDecimal agreedTotal;

    @Schema(description = "Planned end for destination SHORT (and optional for MONTHLY)")
    private LocalDate expectedExitDate;

    @Schema(description = "Rent policy for the new occupancy; default APPLY_NEW")
    private TransferRentPolicy rentPolicy;

    @Schema(description = "Required when rentPolicy is CUSTOM")
    private BigDecimal rentSnapshot;

    private BigDecimal depositSnapshot;
    private Boolean foodEnabled;
    private BigDecimal foodChargeSnapshot;

    @Schema(description = "Ignored at activation; server copies space foodIncludedInRent policy")
    private Boolean foodIncludedInRent;

    @Valid
    private List<OccupancyChargeLineRequest> otherCharges = new ArrayList<>();

    @Valid
    @Schema(description = "Amenities for the new occupancy; omit for all space amenities")
    private List<AmenityAssignmentDto> amenities;
}
