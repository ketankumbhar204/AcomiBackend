package com.amico.amico_backend.accommodation.domain.port;

import java.util.UUID;

public interface AccommodationOccupancyPort {

    boolean spaceHasAnyOccupancy(UUID spaceId);

    boolean hasActiveOccupancyForBed(UUID bedId);

    boolean hasOccupancyHistoryForBed(UUID bedId);

    boolean hasActiveOccupancyForRoom(UUID roomId);

    boolean hasOccupancyHistoryForRoom(UUID roomId);

    boolean hasOccupancyHistoryForFloor(UUID floorId);

    boolean hasOccupancyHistoryForUnit(UUID unitId);

    boolean hasOccupancyHistoryForBuilding(UUID buildingId);
}
