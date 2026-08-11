package com.amico.amico_backend.occupancy.api.dto.response;

import com.amico.amico_backend.occupancy.domain.model.OccupancyChargeCode;
import com.amico.amico_backend.occupancy.infrastructure.persistence.entity.OccupancyChargeSnapshotEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Additional charge line in an occupancy contract snapshot")
public class OccupancyChargeLineResponse {

    private UUID chargeSnapshotId;
    private OccupancyChargeCode code;
    private String label;
    private BigDecimal amount;

    public static OccupancyChargeLineResponse from(OccupancyChargeSnapshotEntity entity) {
        return OccupancyChargeLineResponse.builder()
                .chargeSnapshotId(entity.getId())
                .code(entity.getChargeCode())
                .label(entity.getLabel())
                .amount(entity.getAmount())
                .build();
    }
}
