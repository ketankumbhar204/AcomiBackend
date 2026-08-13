package com.acomi.acomi_backend.accommodation.application.service;

import com.acomi.acomi_backend.accommodation.api.dto.request.BulkCreateBedsRequest;
import com.acomi.acomi_backend.accommodation.api.dto.request.BulkCreateRoomsRequest;
import com.acomi.acomi_backend.accommodation.api.dto.request.BulkCreateUnitsRequest;
import com.acomi.acomi_backend.accommodation.api.dto.response.BulkCreateBedsResponse;
import com.acomi.acomi_backend.accommodation.api.dto.response.BulkCreateRoomsResponse;
import com.acomi.acomi_backend.accommodation.api.dto.response.BulkCreateUnitsResponse;
import com.acomi.acomi_backend.accommodation.domain.model.AccommodationStatus;
import com.acomi.acomi_backend.accommodation.domain.model.BedLabelStyle;
import com.acomi.acomi_backend.accommodation.domain.policy.AccommodationLimits;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccommodationBulkService {

    private final AccommodationAccessService accessService;
    private final AccommodationProfileService profileService;
    private final AccommodationLayoutService layoutService;
    private final SyntheticUnitService syntheticUnitService;
    private final AccommodationNumberingService numberingService;
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final UnitRepository unitRepository;
    private final RoomRepository roomRepository;
    private final BedRepository bedRepository;

    @Transactional
    public BulkCreateUnitsResponse bulkCreateUnits(
            UUID spaceId, UUID buildingId, UUID callerId, BulkCreateUnitsRequest request) {
        log.info("Bulk creating units: spaceId={}, buildingId={}, callerId={}, count={}",
                spaceId, buildingId, callerId, request.getCount());

        profileService.assertUnitsAllowed(spaceId);
        accessService.assertCanManageStructure(spaceId, callerId);
        validateBulkUnitsRequest(request);

        BuildingEntity building = buildingRepository.findActiveByIdAndSpaceId(buildingId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Building", "id", buildingId));
        layoutService.assertVisibleUnitCreationAllowed(building);

        List<String> existing = unitRepository.findActiveUnitNumbersByBuildingId(buildingId);
        List<String> unitNumbers;
        try {
            unitNumbers = numberingService.allocateUnitNumbers(
                    request.getCount(), request.getStartUnitNumber(), existing);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ex.getMessage());
        }

        List<UUID> unitIds = new ArrayList<>(unitNumbers.size());
        AccommodationStatus status = request.resolvedDefaultStatus();
        for (String unitNumber : unitNumbers) {
            UnitEntity unit = unitRepository.save(UnitEntity.builder()
                    .building(building)
                    .name(numberingService.unitDisplayName(unitNumber))
                    .unitNumber(unitNumber)
                    .status(status)
                    .synthetic(false)
                    .build());
            unitIds.add(unit.getId());
        }

        return BulkCreateUnitsResponse.builder()
                .unitsCreated(unitIds.size())
                .unitIds(unitIds)
                .build();
    }

    @Transactional
    public BulkCreateRoomsResponse bulkCreateRoomsUnderFloor(
            UUID spaceId, UUID floorId, UUID callerId, BulkCreateRoomsRequest request) {
        log.info("Bulk creating rooms under floor: spaceId={}, floorId={}, callerId={}, count={}",
                spaceId, floorId, callerId, request.getCount());

        accessService.assertCanManageStructure(spaceId, callerId);
        validateBulkRoomsRequest(spaceId, request);

        FloorEntity floor = floorRepository.findActiveByIdAndSpaceId(floorId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Floor", "id", floorId));

        BuildingEntity building = floor.getBuilding();
        layoutService.assertRoomCreationUnderFloor(building);

        boolean includeGround = floor.getFloorNumber() == 0;
        int floorIndex = includeGround ? floor.getFloorNumber() : floor.getFloorNumber() - 1;
        List<String> existing = roomRepository.findActiveRoomNumbersByFloorId(floorId);
        List<String> roomNumbers = numberingService.allocatePgRoomNumbers(
                floorIndex, request.getCount(), request.getStartRoomNumber(), existing);

        return createCorridorRooms(floor, roomNumbers, request);
    }

    @Transactional
    public BulkCreateRoomsResponse bulkCreateRoomsUnderUnit(
            UUID spaceId, UUID unitId, UUID callerId, BulkCreateRoomsRequest request) {
        log.info("Bulk creating rooms under unit: spaceId={}, unitId={}, callerId={}, count={}",
                spaceId, unitId, callerId, request.getCount());

        profileService.assertRoomsUnderUnitAllowed(spaceId);
        accessService.assertCanManageStructure(spaceId, callerId);
        validateBulkRoomsRequest(spaceId, request);

        UnitEntity unit = unitRepository.findActiveByIdAndSpaceId(unitId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit", "id", unitId));
        layoutService.assertRoomCreationUnderUnit(unit.getBuilding(), unit);
        layoutService.assertUnitVisible(unit);

        List<String> existing = roomRepository.findActiveRoomNumbersByUnitId(unitId);
        List<String> roomNumbers = numberingService.allocateCoLivingRoomNumbers(
                request.getCount(), request.getStartRoomNumber(), existing);

        return createRooms(null, unit, roomNumbers, request);
    }

    @Transactional
    public BulkCreateBedsResponse bulkCreateBeds(
            UUID spaceId, UUID roomId, UUID callerId, BulkCreateBedsRequest request) {
        log.info("Bulk creating beds: spaceId={}, roomId={}, callerId={}, count={}",
                spaceId, roomId, callerId, request.getCount());

        profileService.assertBedsAllowed(spaceId);
        accessService.assertCanManageStructure(spaceId, callerId);

        RoomEntity room = roomRepository.findActiveByIdAndSpaceId(roomId, spaceId)
                .orElseThrow(() -> ResourceNotFoundException.notInSpace("Room", roomId));

        Set<String> existing = new HashSet<>(bedRepository.findActiveBedNumbersByRoomId(roomId));
        List<String> labels = numberingService.bedLabels(request.getCount(), request.getLabelStyle(), existing);

        List<UUID> bedIds = new ArrayList<>(labels.size());
        for (String label : labels) {
            BedEntity bed = bedRepository.save(BedEntity.builder()
                    .room(room)
                    .name(numberingService.bedDisplayName(label))
                    .bedNumber(label)
                    .build());
            bedIds.add(bed.getId());
        }

        return BulkCreateBedsResponse.builder()
                .bedsCreated(bedIds.size())
                .bedIds(bedIds)
                .build();
    }

    private BulkCreateRoomsResponse createCorridorRooms(
            FloorEntity floor, List<String> roomNumbers, BulkCreateRoomsRequest request) {
        List<UUID> roomIds = new ArrayList<>(roomNumbers.size());
        int bedsCreated = 0;

        for (String roomNumber : roomNumbers) {
            UnitEntity syntheticUnit = syntheticUnitService.createSyntheticUnitForRoom(floor, roomNumber);
            RoomEntity room = roomRepository.save(RoomEntity.builder()
                    .unit(syntheticUnit)
                    .name(numberingService.roomDisplayName(roomNumber))
                    .roomNumber(roomNumber)
                    .roomType(request.getRoomType())
                    .capacity(request.getCapacity())
                    .status(request.resolvedDefaultStatus())
                    .build());
            roomIds.add(room.getId());
            bedsCreated += createBedsForRoom(room, request);
        }

        return BulkCreateRoomsResponse.builder()
                .roomsCreated(roomIds.size())
                .bedsCreated(bedsCreated)
                .roomIds(roomIds)
                .build();
    }

    private int createBedsForRoom(RoomEntity room, BulkCreateRoomsRequest request) {
        if (request.getBedsPerRoom() <= 0) {
            return 0;
        }
        int bedsCreated = 0;
        Set<String> existing = new HashSet<>();
        List<String> labels = numberingService.bedLabels(
                request.getBedsPerRoom(), BedLabelStyle.ALPHA, existing);
        for (String label : labels) {
            bedRepository.save(BedEntity.builder()
                    .room(room)
                    .name(numberingService.bedDisplayName(label))
                    .bedNumber(label)
                    .build());
            bedsCreated++;
        }
        return bedsCreated;
    }

    private BulkCreateRoomsResponse createRooms(
            FloorEntity floor,
            UnitEntity unit,
            List<String> roomNumbers,
            BulkCreateRoomsRequest request) {
        List<UUID> roomIds = new ArrayList<>(roomNumbers.size());
        int bedsCreated = 0;

        for (String roomNumber : roomNumbers) {
            RoomEntity room = roomRepository.save(RoomEntity.builder()
                    .floor(floor)
                    .unit(unit)
                    .name(numberingService.roomDisplayName(roomNumber))
                    .roomNumber(roomNumber)
                    .roomType(request.getRoomType())
                    .capacity(request.getCapacity())
                    .status(request.resolvedDefaultStatus())
                    .build());
            roomIds.add(room.getId());

            if (request.getBedsPerRoom() > 0) {
                Set<String> existing = new HashSet<>();
                List<String> labels = numberingService.bedLabels(
                        request.getBedsPerRoom(), BedLabelStyle.ALPHA, existing);
                for (String label : labels) {
                    bedRepository.save(BedEntity.builder()
                            .room(room)
                            .name(numberingService.bedDisplayName(label))
                            .bedNumber(label)
                            .build());
                    bedsCreated++;
                }
            }
        }

        return BulkCreateRoomsResponse.builder()
                .roomsCreated(roomIds.size())
                .bedsCreated(bedsCreated)
                .roomIds(roomIds)
                .build();
    }

    private void validateBulkUnitsRequest(BulkCreateUnitsRequest request) {
        if (request.getCount() > AccommodationLimits.MAX_UNITS_PER_BULK) {
            throw new BusinessException(
                    "Bulk operation exceeds maximum of " + AccommodationLimits.MAX_UNITS_PER_BULK + " units");
        }
    }

    private void validateBulkRoomsRequest(UUID spaceId, BulkCreateRoomsRequest request) {
        if (request.getCount() > AccommodationLimits.MAX_ROOMS_PER_BULK) {
            throw new BusinessException(
                    "Bulk operation exceeds maximum of " + AccommodationLimits.MAX_ROOMS_PER_BULK + " rooms");
        }

        int totalBeds = request.getCount() * request.getBedsPerRoom();
        if (totalBeds > AccommodationLimits.MAX_BEDS_PER_SETUP) {
            throw new BusinessException(
                    "Bulk operation would create more than " + AccommodationLimits.MAX_BEDS_PER_SETUP + " beds");
        }

        if (request.getBedsPerRoom() > 0) {
            profileService.assertBedsAllowed(spaceId);
        }

        SpaceType spaceType = accessService.loadSpaceType(spaceId);
        if (spaceType == SpaceType.RENTAL && request.getBedsPerRoom() > 0) {
            throw new BusinessException("Beds are not supported for Rental spaces");
        }
    }

}
