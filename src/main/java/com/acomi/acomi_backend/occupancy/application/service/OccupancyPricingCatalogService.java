package com.acomi.acomi_backend.occupancy.application.service;

import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BedEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.RoomEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.UnitEntity;
import com.acomi.acomi_backend.occupancy.domain.model.AllocationTargetType;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;
import org.springframework.stereotype.Service;

@Service
public class OccupancyPricingCatalogService {

    public CatalogDefaults resolve(OccupancyTargetService.ResolvedTarget target) {
        return switch (target.getTargetType()) {
            case BED -> fromBed(target.getBed());
            case ROOM -> fromRoom(target.getRoom());
            case UNIT -> fromUnit(target.getUnit());
        };
    }

    private CatalogDefaults fromBed(BedEntity bed) {
        if (bed == null) {
            return CatalogDefaults.empty();
        }
        return CatalogDefaults.builder()
                .defaultRent(bed.getDefaultRent())
                .defaultDeposit(bed.getDefaultDeposit())
                .build();
    }

    private CatalogDefaults fromRoom(RoomEntity room) {
        if (room == null) {
            return CatalogDefaults.empty();
        }
        return CatalogDefaults.builder()
                .defaultRent(room.getDefaultRent())
                .defaultDeposit(room.getDefaultDeposit())
                .build();
    }

    private CatalogDefaults fromUnit(UnitEntity unit) {
        if (unit == null) {
            return CatalogDefaults.empty();
        }
        return CatalogDefaults.builder()
                .defaultRent(unit.getDefaultRent())
                .defaultDeposit(unit.getDefaultDeposit())
                .build();
    }

    @Getter
    @Builder
    public static class CatalogDefaults {
        private final BigDecimal defaultRent;
        private final BigDecimal defaultDeposit;

        public static CatalogDefaults empty() {
            return CatalogDefaults.builder().build();
        }
    }
}
