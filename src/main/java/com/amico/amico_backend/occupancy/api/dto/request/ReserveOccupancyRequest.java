package com.amico.amico_backend.occupancy.api.dto.request;

import com.amico.amico_backend.member.domain.model.MemberCategory;
import com.amico.amico_backend.occupancy.domain.model.AllocationTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
    private MemberCategory memberCategory;
    private String remarks;
}
