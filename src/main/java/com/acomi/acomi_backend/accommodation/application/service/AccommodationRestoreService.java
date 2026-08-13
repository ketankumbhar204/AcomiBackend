package com.acomi.acomi_backend.accommodation.application.service;

import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BedEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.FloorEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.RoomEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.UnitEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.BedRepository;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.BuildingRepository;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.FloorRepository;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.RoomRepository;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.UnitRepository;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccommodationRestoreService {

    private final AccommodationAccessService accessService;
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final UnitRepository unitRepository;
    private final RoomRepository roomRepository;
    private final BedRepository bedRepository;

    @Transactional
    public void restoreBuilding(UUID spaceId, UUID buildingId, UUID callerId) {
        log.info("Restoring building: spaceId={}, buildingId={}, callerId={}", spaceId, buildingId, callerId);
        accessService.assertCanDeactivateStructure(spaceId, callerId);

        BuildingEntity building = buildingRepository.findByIdAndSpaceId(buildingId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Building", "id", buildingId));
        if (building.isActive()) {
            throw new BusinessException("Building is already active.");
        }
        if (buildingRepository.existsBySpaceIdAndNameAndIsActiveTrue(spaceId, building.getName())) {
            throw new BusinessException("An active building with this name already exists in the space.");
        }

        building.setActive(true);
        buildingRepository.save(building);
    }

    @Transactional
    public void restoreFloor(UUID spaceId, UUID floorId, UUID callerId) {
        log.info("Restoring floor: spaceId={}, floorId={}, callerId={}", spaceId, floorId, callerId);
        accessService.assertCanDeactivateStructure(spaceId, callerId);

        FloorEntity floor = floorRepository.findByIdAndSpaceId(floorId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Floor", "id", floorId));
        if (floor.isActive()) {
            throw new BusinessException("Floor is already active.");
        }
        if (!floor.getBuilding().isActive()) {
            throw new BusinessException("Cannot restore floor because its building is deactivated.");
        }
        if (floorRepository.existsByBuildingIdAndFloorNumberAndIsActiveTrue(
                floor.getBuilding().getId(), floor.getFloorNumber())) {
            throw new BusinessException("An active floor with this floor number already exists in the building.");
        }

        floor.setActive(true);
        floorRepository.save(floor);
    }

    @Transactional
    public void restoreUnit(UUID spaceId, UUID unitId, UUID callerId) {
        log.info("Restoring unit: spaceId={}, unitId={}, callerId={}", spaceId, unitId, callerId);
        accessService.assertCanDeactivateStructure(spaceId, callerId);

        UnitEntity unit = unitRepository.findByIdAndSpaceId(unitId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit", "id", unitId));
        if (unit.isActive()) {
            throw new BusinessException("Unit is already active.");
        }
        if (!unit.getBuilding().isActive()) {
            throw new BusinessException("Cannot restore unit because its building is deactivated.");
        }
        if (unitRepository.existsByBuildingIdAndUnitNumberAndIsActiveTrue(
                unit.getBuilding().getId(), unit.getUnitNumber())) {
            throw new BusinessException("An active unit with this unit number already exists in the building.");
        }

        unit.setActive(true);
        unitRepository.save(unit);
    }

    @Transactional
    public void restoreRoom(UUID spaceId, UUID roomId, UUID callerId) {
        log.info("Restoring room: spaceId={}, roomId={}, callerId={}", spaceId, roomId, callerId);
        accessService.assertCanDeactivateStructure(spaceId, callerId);

        RoomEntity room = roomRepository.findByIdAndSpaceId(roomId, spaceId)
                .orElseThrow(() -> ResourceNotFoundException.notInSpace("Room", roomId));
        if (room.isActive()) {
            throw new BusinessException("Room is already active.");
        }
        if (room.getFloor() != null && !room.getFloor().isActive()) {
            throw new BusinessException("Cannot restore room because its floor is deactivated.");
        }
        if (room.getUnit() != null && !room.getUnit().isActive()) {
            throw new BusinessException("Cannot restore room because its unit is deactivated.");
        }

        room.setActive(true);
        roomRepository.save(room);
    }

    @Transactional
    public void restoreBed(UUID spaceId, UUID bedId, UUID callerId) {
        log.info("Restoring bed: spaceId={}, bedId={}, callerId={}", spaceId, bedId, callerId);
        accessService.assertCanDeactivateStructure(spaceId, callerId);

        BedEntity bed = bedRepository.findByIdAndSpaceId(bedId, spaceId)
                .orElseThrow(() -> ResourceNotFoundException.notInSpace("Bed", bedId));
        if (bed.isActive()) {
            throw new BusinessException("Bed is already active.");
        }
        if (!bed.getRoom().isActive()) {
            throw new BusinessException("Cannot restore bed because its room is deactivated.");
        }
        if (bedRepository.existsByRoomIdAndBedNumberAndIsActiveTrue(
                bed.getRoom().getId(), bed.getBedNumber())) {
            throw new BusinessException("An active bed with this bed number already exists in the room.");
        }

        bed.setActive(true);
        bedRepository.save(bed);
    }
}
