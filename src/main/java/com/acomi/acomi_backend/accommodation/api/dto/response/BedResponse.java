package com.acomi.acomi_backend.accommodation.api.dto.response;

import com.acomi.acomi_backend.accommodation.domain.model.AccommodationStatus;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BedEntity;
import com.acomi.acomi_backend.occupancy.api.dto.response.BedOccupantSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Bed within a room")
public class BedResponse {

    private UUID bedId;
    private UUID roomId;
    private String name;
    private String bedNumber;
    private AccommodationStatus status;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private AccommodationActionMetadata actions;
    private BedOccupantSummaryResponse occupant;
    private BigDecimal defaultRent;
    private BigDecimal defaultDeposit;

    public static BedResponse from(BedEntity bed) {
        return from(bed, null, null);
    }

    public static BedResponse from(BedEntity bed, AccommodationActionMetadata actions) {
        return from(bed, actions, null);
    }

    public static BedResponse from(BedEntity bed, AccommodationActionMetadata actions, BedOccupantSummaryResponse occupant) {
        return BedResponse.builder()
                .bedId(bed.getId())
                .roomId(bed.getRoom().getId())
                .name(bed.getName())
                .bedNumber(bed.getBedNumber())
                .status(bed.getStatus())
                .active(bed.isActive())
                .createdAt(bed.getCreatedAt())
                .updatedAt(bed.getUpdatedAt())
                .actions(actions)
                .occupant(occupant)
                .defaultRent(bed.getDefaultRent())
                .defaultDeposit(bed.getDefaultDeposit())
                .build();
    }
}
