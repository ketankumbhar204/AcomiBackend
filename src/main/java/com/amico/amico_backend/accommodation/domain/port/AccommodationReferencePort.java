package com.amico.amico_backend.accommodation.domain.port;

import java.util.UUID;

public interface AccommodationReferencePort {

    boolean isBedReferenced(UUID bedId);

    boolean isRoomReferenced(UUID roomId);

    boolean isFloorReferenced(UUID floorId);

    boolean isUnitReferenced(UUID unitId);

    boolean isBuildingReferenced(UUID buildingId);
}
