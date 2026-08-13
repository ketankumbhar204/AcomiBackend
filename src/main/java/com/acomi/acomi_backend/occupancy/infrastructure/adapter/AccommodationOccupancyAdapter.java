package com.acomi.acomi_backend.occupancy.infrastructure.adapter;

import com.acomi.acomi_backend.accommodation.domain.port.AccommodationOccupancyPort;
import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.repository.OccupancyRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccommodationOccupancyAdapter implements AccommodationOccupancyPort {

    private final OccupancyRepository occupancyRepository;

    @Override
    public boolean spaceHasAnyOccupancy(UUID spaceId) {
        return occupancyRepository.existsBySpaceId(spaceId);
    }

    @Override
    public boolean hasActiveOccupancyForBed(UUID bedId) {
        return occupancyRepository.existsByBedIdAndStatus(bedId, OccupancyStatus.ACTIVE)
                || occupancyRepository.existsByBedIdAndStatus(bedId, OccupancyStatus.RESERVED);
    }

    @Override
    public boolean hasOccupancyHistoryForBed(UUID bedId) {
        return occupancyRepository.existsByBedId(bedId);
    }

    @Override
    public boolean hasActiveOccupancyForRoom(UUID roomId) {
        return occupancyRepository.existsByRoomIdAndStatus(roomId, OccupancyStatus.ACTIVE)
                || occupancyRepository.existsByRoomIdAndStatus(roomId, OccupancyStatus.RESERVED);
    }

    @Override
    public boolean hasOccupancyHistoryForRoom(UUID roomId) {
        return occupancyRepository.existsByRoomId(roomId);
    }

    @Override
    public boolean hasOccupancyHistoryForFloor(UUID floorId) {
        return occupancyRepository.existsByFloorId(floorId);
    }

    @Override
    public boolean hasOccupancyHistoryForUnit(UUID unitId) {
        return occupancyRepository.existsByUnitId(unitId);
    }

    @Override
    public boolean hasOccupancyHistoryForBuilding(UUID buildingId) {
        return occupancyRepository.existsByBuildingId(buildingId);
    }
}
