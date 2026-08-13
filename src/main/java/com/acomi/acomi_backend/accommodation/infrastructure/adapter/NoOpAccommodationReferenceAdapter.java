package com.acomi.acomi_backend.accommodation.infrastructure.adapter;

import com.acomi.acomi_backend.accommodation.domain.port.AccommodationReferencePort;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class NoOpAccommodationReferenceAdapter implements AccommodationReferencePort {

    @Override
    public boolean isBedReferenced(UUID bedId) {
        return false;
    }

    @Override
    public boolean isRoomReferenced(UUID roomId) {
        return false;
    }

    @Override
    public boolean isFloorReferenced(UUID floorId) {
        return false;
    }

    @Override
    public boolean isUnitReferenced(UUID unitId) {
        return false;
    }

    @Override
    public boolean isBuildingReferenced(UUID buildingId) {
        return false;
    }
}
