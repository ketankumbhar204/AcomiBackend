package com.acomi.acomi_backend.accommodation.application.service;

import com.acomi.acomi_backend.accommodation.api.dto.request.CreateRoomRequest;
import com.acomi.acomi_backend.accommodation.api.dto.request.UpdateRoomRequest;
import com.acomi.acomi_backend.accommodation.api.dto.response.RoomResponse;
import com.acomi.acomi_backend.accommodation.domain.model.AccommodationStatus;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.FloorEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.RoomEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.UnitEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.BedRepository;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.FloorRepository;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.RoomRepository;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.UnitRepository;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final FloorRepository floorRepository;
    private final UnitRepository unitRepository;
    private final BedRepository bedRepository;
    private final AccommodationAccessService accessService;
    private final AccommodationProfileService profileService;
    private final AccommodationActionService actionService;
    private final AccommodationLayoutService layoutService;
    private final SyntheticUnitService syntheticUnitService;

    @Transactional
    public RoomResponse createRoomUnderFloor(
            UUID spaceId, UUID floorId, UUID callerId, CreateRoomRequest request) {
        log.info("Creating room under floor: spaceId={}, floorId={}, callerId={}, roomNumber={}",
                spaceId, floorId, callerId, request.getRoomNumber());

        profileService.assertBedsAllowed(spaceId);
        accessService.assertCanManageStructure(spaceId, callerId);

        FloorEntity floor = floorRepository.findActiveByIdAndSpaceId(floorId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Floor", "id", floorId));

        BuildingContext building = buildingFromFloor(floor);
        layoutService.assertRoomCreationUnderFloor(building.entity());

        UnitEntity syntheticUnit = syntheticUnitService.createSyntheticUnitForRoom(floor, request.getRoomNumber());
        RoomEntity room = buildRoom(request, null, syntheticUnit);
        layoutService.validateRoomEntity(room, building.entity());
        room = roomRepository.save(room);
        return RoomResponse.from(room);
    }

    @Transactional
    public RoomResponse createRoomUnderUnit(
            UUID spaceId, UUID unitId, UUID callerId, CreateRoomRequest request) {
        log.info("Creating room under unit: spaceId={}, unitId={}, callerId={}, roomNumber={}",
                spaceId, unitId, callerId, request.getRoomNumber());

        accessService.assertCanManageStructure(spaceId, callerId);

        UnitEntity unit = unitRepository.findActiveByIdAndSpaceId(unitId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit", "id", unitId));

        BuildingContext building = buildingFromUnit(unit);
        layoutService.assertRoomCreationUnderUnit(building.entity(), unit);
        profileService.assertBedsAllowed(spaceId);

        RoomEntity room = buildRoom(request, null, unit);
        layoutService.validateRoomEntity(room, building.entity());
        room = roomRepository.save(room);
        return RoomResponse.from(room);
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> getRoomsByFloor(UUID spaceId, UUID floorId, UUID callerId) {
        log.info("Listing rooms by floor: spaceId={}, floorId={}, callerId={}", spaceId, floorId, callerId);

        accessService.assertCanViewStructure(spaceId, callerId);
        floorRepository.findActiveByIdAndSpaceId(floorId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Floor", "id", floorId));

        return roomRepository.findActiveByFloorIdIncludingUnits(floorId)
                .stream()
                .map(RoomResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> getRoomsByUnit(UUID spaceId, UUID unitId, UUID callerId) {
        log.info("Listing rooms by unit: spaceId={}, unitId={}, callerId={}", spaceId, unitId, callerId);

        accessService.assertCanViewStructure(spaceId, callerId);
        UnitEntity unit = unitRepository.findActiveByIdAndSpaceId(unitId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit", "id", unitId));
        layoutService.assertUnitVisible(unit);

        return roomRepository.findActiveByUnitId(unitId)
                .stream()
                .map(RoomResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public RoomResponse getRoom(UUID spaceId, UUID roomId, UUID callerId) {
        log.info("Fetching room: spaceId={}, roomId={}, callerId={}", spaceId, roomId, callerId);

        accessService.assertCanViewStructure(spaceId, callerId);

        RoomEntity room = roomRepository.findActiveByIdAndSpaceId(roomId, spaceId)
                .orElseThrow(() -> ResourceNotFoundException.notInSpace("Room", roomId));

        return RoomResponse.from(room, actionService.forRoom(spaceId, room, callerId));
    }

    @Transactional
    public RoomResponse updateRoom(
            UUID spaceId, UUID roomId, UUID callerId, UpdateRoomRequest request) {
        log.info("Updating room: spaceId={}, roomId={}, callerId={}", spaceId, roomId, callerId);

        accessService.assertCanManageStructure(spaceId, callerId);

        RoomEntity room = roomRepository.findActiveByIdAndSpaceId(roomId, spaceId)
                .orElseThrow(() -> ResourceNotFoundException.notInSpace("Room", roomId));

        room.setName(request.getName());
        room.setRoomNumber(request.getRoomNumber());
        room.setRoomType(request.getRoomType());
        room.setCapacity(request.getCapacity());
        room.setStatus(request.getStatus());
        room.setDefaultRent(request.getDefaultRent());
        room.setDefaultDeposit(request.getDefaultDeposit());

        return RoomResponse.from(roomRepository.save(room));
    }

    @Transactional
    public void deactivateRoom(UUID spaceId, UUID roomId, UUID callerId) {
        log.info("Deactivating room: spaceId={}, roomId={}, callerId={}", spaceId, roomId, callerId);

        accessService.assertCanDeactivateStructure(spaceId, callerId);

        RoomEntity room = roomRepository.findActiveByIdAndSpaceId(roomId, spaceId)
                .orElseThrow(() -> ResourceNotFoundException.notInSpace("Room", roomId));

        if (bedRepository.existsByRoomIdAndIsActiveTrue(roomId)) {
            throw new BusinessException(
                    "Cannot deactivate room while active beds exist. Deactivate beds first.");
        }

        room.setActive(false);
        roomRepository.save(room);
    }

    private RoomEntity buildRoom(CreateRoomRequest request, FloorEntity floor, UnitEntity unit) {
        AccommodationStatus status = request.getStatus() != null
                ? request.getStatus()
                : AccommodationStatus.AVAILABLE;

        return RoomEntity.builder()
                .floor(floor)
                .unit(unit)
                .name(request.getName())
                .roomNumber(request.getRoomNumber())
                .roomType(request.getRoomType())
                .capacity(request.getCapacity())
                .status(status)
                .build();
    }

    private BuildingContext buildingFromFloor(FloorEntity floor) {
        return new BuildingContext(floor.getBuilding());
    }

    private BuildingContext buildingFromUnit(UnitEntity unit) {
        return new BuildingContext(unit.getBuilding());
    }

    private record BuildingContext(
            com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BuildingEntity entity) {}
}
