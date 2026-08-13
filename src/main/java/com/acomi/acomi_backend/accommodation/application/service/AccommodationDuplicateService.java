package com.acomi.acomi_backend.accommodation.application.service;

import com.acomi.acomi_backend.accommodation.api.dto.request.DuplicateBuildingRequest;
import com.acomi.acomi_backend.accommodation.api.dto.request.DuplicateFloorRequest;
import com.acomi.acomi_backend.accommodation.api.dto.request.DuplicateRoomRequest;
import com.acomi.acomi_backend.accommodation.api.dto.response.DuplicateBuildingResponse;
import com.acomi.acomi_backend.accommodation.api.dto.response.DuplicateFloorResponse;
import com.acomi.acomi_backend.accommodation.api.dto.response.DuplicateRoomResponse;
import com.acomi.acomi_backend.accommodation.domain.model.PropertyLayoutMode;
import com.acomi.acomi_backend.accommodation.domain.policy.AccommodationNumberingService;
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
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccommodationDuplicateService {

    private final AccommodationAccessService accessService;
    private final AccommodationProfileService profileService;
    private final AccommodationNumberingService numberingService;
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final UnitRepository unitRepository;
    private final RoomRepository roomRepository;
    private final BedRepository bedRepository;

    @Transactional
    public DuplicateBuildingResponse duplicateBuilding(
            UUID spaceId, UUID buildingId, UUID callerId, DuplicateBuildingRequest request) {
        log.info("Duplicating building: spaceId={}, buildingId={}, callerId={}",
                spaceId, buildingId, callerId);

        accessService.assertCanManageStructure(spaceId, callerId);

        BuildingEntity sourceBuilding = buildingRepository.findActiveByIdAndSpaceId(buildingId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Building", "id", buildingId));

        if (buildingRepository.existsBySpaceIdAndNameAndIsActiveTrue(spaceId, request.getTargetBuildingName())) {
            throw new BusinessException("An active building with this name already exists in the space");
        }

        BuildingEntity targetBuilding = buildingRepository.save(BuildingEntity.builder()
                .space(sourceBuilding.getSpace())
                .name(request.getTargetBuildingName())
                .code(request.getTargetBuildingCode())
                .layoutMode(sourceBuilding.getLayoutMode())
                .build());

        SpaceType spaceType = sourceBuilding.getSpace().getType();
        int floorsCreated = 0;
        int unitsCreated = 0;
        int roomsCreated = 0;
        int bedsCreated = 0;

        switch (spaceType) {
            case PG, HOSTEL -> {
                profileService.assertFloorsAllowed(spaceId);
                profileService.assertRoomsUnderFloorAllowed(spaceId);
                profileService.assertBedsAllowed(spaceId);
                for (FloorEntity sourceFloor : floorRepository.findActiveByBuildingId(buildingId)) {
                    FloorEntity targetFloor = floorRepository.save(FloorEntity.builder()
                            .building(targetBuilding)
                            .name(sourceFloor.getName())
                            .floorNumber(sourceFloor.getFloorNumber())
                            .sortOrder(sourceFloor.getSortOrder())
                            .build());
                    floorsCreated++;
                    if (sourceBuilding.getLayoutMode() == PropertyLayoutMode.APARTMENT_PG) {
                        for (UnitEntity sourceUnit :
                                unitRepository.findActiveByFloorId(sourceFloor.getId(), false)) {
                            UnitEntity targetUnit = cloneUnit(sourceUnit, targetBuilding, targetFloor);
                            unitsCreated++;
                            int[] roomBedCounts = cloneRoomsAndBeds(
                                    roomRepository.findActiveByUnitId(sourceUnit.getId()), null, targetUnit);
                            roomsCreated += roomBedCounts[0];
                            bedsCreated += roomBedCounts[1];
                        }
                    } else {
                        int targetFloorIndex = resolveFloorIndex(
                                targetFloor.getFloorNumber(), targetFloor.getFloorNumber() == 0);
                        int[] roomBedCounts = cloneCorridorFloorRooms(
                                roomRepository.findActiveByFloorIdIncludingUnits(sourceFloor.getId()),
                                targetFloor,
                                targetFloorIndex,
                                true);
                        roomsCreated += roomBedCounts[0];
                        bedsCreated += roomBedCounts[1];
                        unitsCreated += roomBedCounts[2];
                    }
                }
            }
            case CO_LIVING -> {
                profileService.assertUnitsAllowed(spaceId);
                profileService.assertRoomsUnderUnitAllowed(spaceId);
                profileService.assertBedsAllowed(spaceId);
                for (UnitEntity sourceUnit : unitRepository.findActiveByBuildingId(buildingId, true)) {
                    UnitEntity targetUnit = cloneUnit(sourceUnit, targetBuilding);
                    unitsCreated++;
                    int[] roomBedCounts = cloneRoomsAndBeds(
                            roomRepository.findActiveByUnitId(sourceUnit.getId()), null, targetUnit);
                    roomsCreated += roomBedCounts[0];
                    bedsCreated += roomBedCounts[1];
                }
            }
            case RENTAL -> {
                profileService.assertUnitsAllowed(spaceId);
                for (UnitEntity sourceUnit : unitRepository.findActiveByBuildingId(buildingId, true)) {
                    cloneUnit(sourceUnit, targetBuilding);
                    unitsCreated++;
                }
            }
            default -> throw new BusinessException("Building duplication is not supported for this space type");
        }

        return DuplicateBuildingResponse.builder()
                .buildingId(targetBuilding.getId())
                .name(targetBuilding.getName())
                .code(targetBuilding.getCode())
                .floorsCreated(floorsCreated)
                .unitsCreated(unitsCreated)
                .roomsCreated(roomsCreated)
                .bedsCreated(bedsCreated)
                .build();
    }

    @Transactional
    public DuplicateFloorResponse duplicateFloor(
            UUID spaceId, UUID buildingId, UUID floorId, UUID callerId, DuplicateFloorRequest request) {
        log.info("Duplicating floor: spaceId={}, buildingId={}, floorId={}, callerId={}",
                spaceId, buildingId, floorId, callerId);

        profileService.assertFloorsAllowed(spaceId);
        profileService.assertRoomsUnderFloorAllowed(spaceId);
        profileService.assertBedsAllowed(spaceId);
        accessService.assertCanManageStructure(spaceId, callerId);

        buildingRepository.findActiveByIdAndSpaceId(buildingId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Building", "id", buildingId));

        FloorEntity sourceFloor = floorRepository.findActiveByIdAndBuildingId(floorId, buildingId)
                .orElseThrow(() -> new ResourceNotFoundException("Floor", "id", floorId));

        if (floorRepository.existsByBuildingIdAndFloorNumberAndIsActiveTrue(
                buildingId, request.getTargetFloorNumber())) {
            throw new BusinessException("An active floor with this floor number already exists in the building");
        }

        boolean includeGround = floorRepository.existsByBuildingIdAndFloorNumberAndIsActiveTrue(buildingId, 0);
        String floorName = request.getTargetName() != null && !request.getTargetName().isBlank()
                ? request.getTargetName()
                : numberingService.floorDisplayName(request.getTargetFloorNumber(), includeGround);

        FloorEntity targetFloor = floorRepository.save(FloorEntity.builder()
                .building(sourceFloor.getBuilding())
                .name(floorName)
                .floorNumber(request.getTargetFloorNumber())
                .sortOrder(request.getTargetFloorNumber())
                .build());

        PropertyLayoutMode layoutMode = sourceFloor.getBuilding().getLayoutMode();
        List<RoomEntity> sourceRooms = layoutMode == PropertyLayoutMode.APARTMENT_PG
                ? List.of()
                : roomRepository.findActiveByFloorIdIncludingUnits(floorId);
        int targetFloorIndex = resolveFloorIndex(request.getTargetFloorNumber(), includeGround);

        int roomsCreated = 0;
        int bedsCreated = 0;
        if (layoutMode == PropertyLayoutMode.APARTMENT_PG) {
            List<UnitEntity> sourceUnits = unitRepository.findActiveByFloorId(floorId, false);
            List<String> targetUnitNumbers = allocateApartmentUnitNumbers(
                    targetFloor.getBuilding().getId(),
                    targetFloorIndex,
                    sourceUnits.size(),
                    request.isRenumberRooms(),
                    sourceUnits);
            for (int unitIndex = 0; unitIndex < sourceUnits.size(); unitIndex++) {
                UnitEntity sourceUnit = sourceUnits.get(unitIndex);
                UnitEntity targetUnit = cloneUnitWithNumber(
                        sourceUnit,
                        targetFloor.getBuilding(),
                        targetFloor,
                        targetUnitNumbers.get(unitIndex));
                int[] counts = cloneRoomsAndBeds(
                        roomRepository.findActiveByUnitId(sourceUnit.getId()), null, targetUnit);
                roomsCreated += counts[0];
                bedsCreated += counts[1];
            }
        } else {
            int[] counts = cloneCorridorFloorRooms(
                    sourceRooms, targetFloor, targetFloorIndex, request.isRenumberRooms());
            roomsCreated += counts[0];
            bedsCreated += counts[1];
        }

        return DuplicateFloorResponse.builder()
                .floorId(targetFloor.getId())
                .floorNumber(targetFloor.getFloorNumber())
                .roomsCreated(roomsCreated)
                .bedsCreated(bedsCreated)
                .build();
    }

    @Transactional
    public DuplicateRoomResponse duplicateRoom(
            UUID spaceId, UUID roomId, UUID callerId, DuplicateRoomRequest request) {
        log.info("Duplicating room: spaceId={}, roomId={}, callerId={}", spaceId, roomId, callerId);

        accessService.assertCanManageStructure(spaceId, callerId);

        RoomEntity sourceRoom = roomRepository.findActiveByIdAndSpaceId(roomId, spaceId)
                .orElseThrow(() -> ResourceNotFoundException.notInSpace("Room", roomId));

        if (sourceRoom.getFloor() != null) {
            profileService.assertRoomsUnderFloorAllowed(spaceId);
            profileService.assertBedsAllowed(spaceId);
        } else {
            profileService.assertRoomsUnderUnitAllowed(spaceId);
            profileService.assertBedsAllowed(spaceId);
        }

        String roomNumber = resolveTargetRoomNumber(sourceRoom, request.getTargetRoomNumber());

        RoomEntity newRoom = roomRepository.save(RoomEntity.builder()
                .floor(sourceRoom.getFloor())
                .unit(sourceRoom.getUnit())
                .name(numberingService.roomDisplayName(roomNumber))
                .roomNumber(roomNumber)
                .roomType(sourceRoom.getRoomType())
                .capacity(sourceRoom.getCapacity())
                .status(sourceRoom.getStatus())
                .build());

        int bedsCreated = cloneBeds(sourceRoom, newRoom);

        return DuplicateRoomResponse.builder()
                .roomId(newRoom.getId())
                .roomNumber(newRoom.getRoomNumber())
                .bedsCreated(bedsCreated)
                .build();
    }

    private UnitEntity cloneUnit(UnitEntity sourceUnit, BuildingEntity targetBuilding) {
        return cloneUnit(sourceUnit, targetBuilding, sourceUnit.getFloor());
    }

    private UnitEntity cloneUnit(UnitEntity sourceUnit, BuildingEntity targetBuilding, FloorEntity targetFloor) {
        return cloneUnitWithNumber(
                sourceUnit, targetBuilding, targetFloor, sourceUnit.getUnitNumber());
    }

    private UnitEntity cloneUnitWithNumber(
            UnitEntity sourceUnit,
            BuildingEntity targetBuilding,
            FloorEntity targetFloor,
            String unitNumber) {
        if (unitRepository.existsByBuildingIdAndUnitNumberAndIsActiveTrue(targetBuilding.getId(), unitNumber)) {
            throw new BusinessException(
                    "Unit number " + unitNumber + " already exists in the target building");
        }
        return unitRepository.save(UnitEntity.builder()
                .building(targetBuilding)
                .floor(targetFloor)
                .name(numberingService.unitDisplayName(unitNumber))
                .unitNumber(unitNumber)
                .status(sourceUnit.getStatus())
                .synthetic(sourceUnit.isSynthetic())
                .unitKind(sourceUnit.getUnitKind())
                .build());
    }

    private List<String> allocateApartmentUnitNumbers(
            UUID buildingId,
            int targetFloorIndex,
            int count,
            boolean renumber,
            List<UnitEntity> sourceUnits) {
        if (renumber) {
            return numberingService.nextUnitNumbers(
                    String.valueOf((targetFloorIndex + 1) * 100 + 1), 1, count);
        }
        List<String> numbers = new ArrayList<>(count);
        for (UnitEntity sourceUnit : sourceUnits) {
            String unitNumber = sourceUnit.getUnitNumber();
            if (unitRepository.existsByBuildingIdAndUnitNumberAndIsActiveTrue(buildingId, unitNumber)) {
                throw new BusinessException(
                        "Unit number " + unitNumber + " already exists in the target building. "
                                + "Enable renumbering to assign floor-specific apartment numbers.");
            }
            numbers.add(unitNumber);
        }
        return numbers;
    }

    private int[] cloneCorridorFloorRooms(
            List<RoomEntity> sourceRooms,
            FloorEntity targetFloor,
            int targetFloorIndex,
            boolean renumberRooms) {
        int roomsCreated = 0;
        int bedsCreated = 0;
        int unitsCreated = 0;
        int roomPosition = 1;
        for (RoomEntity sourceRoom : sourceRooms) {
            String roomNumber = renumberRooms
                    ? numberingService.pgRoomNumber(targetFloorIndex, roomPosition++)
                    : sourceRoom.getRoomNumber();
            if (unitRepository.existsByBuildingIdAndUnitNumberAndIsActiveTrue(
                    targetFloor.getBuilding().getId(), roomNumber)) {
                throw new BusinessException(
                        "Unit number " + roomNumber + " already exists in the target building. "
                                + "Enable renumbering to assign floor-specific room numbers.");
            }

            UnitEntity syntheticUnit = unitRepository.save(UnitEntity.builder()
                    .building(targetFloor.getBuilding())
                    .floor(targetFloor)
                    .name(numberingService.unitDisplayName(roomNumber))
                    .unitNumber(roomNumber)
                    .status(sourceRoom.getStatus())
                    .synthetic(true)
                    .build());
            unitsCreated++;
            RoomEntity newRoom = roomRepository.save(RoomEntity.builder()
                    .unit(syntheticUnit)
                    .name(numberingService.roomDisplayName(roomNumber))
                    .roomNumber(roomNumber)
                    .roomType(sourceRoom.getRoomType())
                    .capacity(sourceRoom.getCapacity())
                    .status(sourceRoom.getStatus())
                    .build());
            roomsCreated++;
            bedsCreated += cloneBeds(sourceRoom, newRoom);
        }
        return new int[] {roomsCreated, bedsCreated, unitsCreated};
    }

    private int[] cloneRoomsAndBeds(List<RoomEntity> sourceRooms, FloorEntity targetFloor, UnitEntity targetUnit) {
        int roomsCreated = 0;
        int bedsCreated = 0;
        UUID parentId = targetFloor != null ? targetFloor.getId() : targetUnit.getId();

        for (RoomEntity sourceRoom : sourceRooms) {
            if (targetFloor != null
                    && roomRepository.existsByFloorIdAndRoomNumberAndIsActiveTrue(
                            parentId, sourceRoom.getRoomNumber())) {
                throw new BusinessException(
                        "Room number " + sourceRoom.getRoomNumber() + " already exists on target floor");
            }
            if (targetUnit != null
                    && roomRepository.existsByUnitIdAndRoomNumberAndIsActiveTrue(
                            parentId, sourceRoom.getRoomNumber())) {
                throw new BusinessException(
                        "Room number " + sourceRoom.getRoomNumber() + " already exists on target unit");
            }

            RoomEntity newRoom = roomRepository.save(RoomEntity.builder()
                    .floor(targetFloor)
                    .unit(targetUnit)
                    .name(sourceRoom.getName())
                    .roomNumber(sourceRoom.getRoomNumber())
                    .roomType(sourceRoom.getRoomType())
                    .capacity(sourceRoom.getCapacity())
                    .status(sourceRoom.getStatus())
                    .build());
            roomsCreated++;
            bedsCreated += cloneBeds(sourceRoom, newRoom);
        }
        return new int[] {roomsCreated, bedsCreated};
    }

    private int cloneBeds(RoomEntity sourceRoom, RoomEntity targetRoom) {
        int bedsCreated = 0;
        for (BedEntity sourceBed : bedRepository.findActiveByRoomId(sourceRoom.getId())) {
            if (bedRepository.existsByRoomIdAndBedNumberAndIsActiveTrue(
                    targetRoom.getId(), sourceBed.getBedNumber())) {
                throw new BusinessException(
                        "Bed number " + sourceBed.getBedNumber() + " already exists in the new room");
            }
            bedRepository.save(BedEntity.builder()
                    .room(targetRoom)
                    .name(sourceBed.getName())
                    .bedNumber(sourceBed.getBedNumber())
                    .status(sourceBed.getStatus())
                    .build());
            bedsCreated++;
        }
        return bedsCreated;
    }

    private String resolveTargetRoomNumber(RoomEntity sourceRoom, String requestedNumber) {
        if (requestedNumber != null && !requestedNumber.isBlank()) {
            if (sourceRoom.getFloor() != null
                    && roomRepository.existsByFloorIdAndRoomNumberAndIsActiveTrue(
                            sourceRoom.getFloor().getId(), requestedNumber)) {
                throw new BusinessException("Room number " + requestedNumber + " already exists on this floor");
            }
            if (sourceRoom.getUnit() != null
                    && roomRepository.existsByUnitIdAndRoomNumberAndIsActiveTrue(
                            sourceRoom.getUnit().getId(), requestedNumber)) {
                throw new BusinessException("Room number " + requestedNumber + " already exists on this unit");
            }
            return requestedNumber;
        }

        if (sourceRoom.getFloor() != null) {
            List<String> existing = new ArrayList<>(roomRepository.findActiveRoomNumbersByFloorId(
                    sourceRoom.getFloor().getId()));
            boolean includeGround = sourceRoom.getFloor().getFloorNumber() == 0;
            int floorIndex = resolveFloorIndex(sourceRoom.getFloor().getFloorNumber(), includeGround);
            return numberingService.suggestNextPgRoomNumber(floorIndex, existing);
        }

        List<String> existing = roomRepository.findActiveRoomNumbersByUnitId(sourceRoom.getUnit().getId());
        return numberingService.suggestNextCoLivingRoomLabel(existing);
    }

    private int resolveFloorIndex(int floorNumber, boolean includeGround) {
        return includeGround ? floorNumber : floorNumber - 1;
    }
}
