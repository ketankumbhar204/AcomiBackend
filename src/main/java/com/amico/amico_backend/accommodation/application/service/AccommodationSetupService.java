package com.amico.amico_backend.accommodation.application.service;

import com.amico.amico_backend.accommodation.api.dto.request.setup.AccommodationSetupRequest;
import com.amico.amico_backend.accommodation.api.dto.request.setup.PgHostelSetupConfig;
import com.amico.amico_backend.accommodation.api.dto.request.setup.UnitSetupConfig;
import com.amico.amico_backend.accommodation.api.dto.response.setup.AccommodationSetupPreviewResponse;
import com.amico.amico_backend.accommodation.api.dto.response.setup.AccommodationSetupResultResponse;
import com.amico.amico_backend.accommodation.api.dto.response.setup.AccommodationSetupSampleNode;
import com.amico.amico_backend.accommodation.api.dto.response.setup.AccommodationSetupTotals;
import com.amico.amico_backend.accommodation.domain.model.AccommodationStatus;
import com.amico.amico_backend.accommodation.domain.model.BedLabelStyle;
import com.amico.amico_backend.accommodation.domain.model.PropertyLayoutMode;
import com.amico.amico_backend.accommodation.domain.policy.AccommodationLimits;
import com.amico.amico_backend.accommodation.domain.policy.AccommodationNumberingService;
import com.amico.amico_backend.accommodation.domain.policy.PropertyLayoutModeResolver;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.AccommodationSetupIdempotencyEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.BedEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.FloorEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.RoomEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.UnitEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.repository.AccommodationSetupIdempotencyRepository;
import com.amico.amico_backend.accommodation.infrastructure.persistence.repository.BedRepository;
import com.amico.amico_backend.accommodation.infrastructure.persistence.repository.BuildingRepository;
import com.amico.amico_backend.accommodation.infrastructure.persistence.repository.FloorRepository;
import com.amico.amico_backend.accommodation.infrastructure.persistence.repository.RoomRepository;
import com.amico.amico_backend.accommodation.infrastructure.persistence.repository.UnitRepository;
import com.amico.amico_backend.common.exception.BusinessException;
import com.amico.amico_backend.common.exception.ResourceNotFoundException;
import com.amico.amico_backend.space.domain.model.SpaceType;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.amico.amico_backend.user.infrastructure.persistence.entity.UserEntity;
import com.amico.amico_backend.user.infrastructure.persistence.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccommodationSetupService {

    private final AccommodationAccessService accessService;
    private final AccommodationProfileService profileService;
    private final PropertyLayoutModeResolver layoutModeResolver;
    private final AccommodationNumberingService numberingService;
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final UnitRepository unitRepository;
    private final RoomRepository roomRepository;
    private final BedRepository bedRepository;
    private final AccommodationSetupIdempotencyRepository idempotencyRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public AccommodationSetupPreviewResponse preview(
            UUID spaceId, UUID callerId, AccommodationSetupRequest request) {
        log.info("Previewing accommodation setup: spaceId={}, callerId={}", spaceId, callerId);

        SpaceEntity space = validateSetupRequest(spaceId, callerId, request);
        PropertyLayoutMode layoutMode = resolveLayoutMode(space, request);
        SetupComputation computation = applySetup(space.getType(), layoutMode, request, null, true);

        return AccommodationSetupPreviewResponse.builder()
                .totals(computation.totals())
                .sample(computation.sample())
                .warnings(computation.warnings())
                .build();
    }

    @Transactional
    public AccommodationSetupResultResponse execute(
            UUID spaceId, UUID callerId, AccommodationSetupRequest request, String idempotencyKey) {
        log.info("Executing accommodation setup: spaceId={}, callerId={}", spaceId, callerId);

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException("Idempotency-Key header is required", HttpStatus.BAD_REQUEST);
        }
        if (idempotencyKey.length() > 128) {
            throw new BusinessException("Idempotency-Key must be at most 128 characters", HttpStatus.BAD_REQUEST);
        }

        var existing = idempotencyRepository.findBySpaceIdAndIdempotencyKey(spaceId, idempotencyKey);
        if (existing.isPresent()) {
            AccommodationSetupTotals totals = deserializeTotals(existing.get().getTotalsJson());
            return AccommodationSetupResultResponse.builder()
                    .buildingId(existing.get().getBuilding().getId())
                    .totals(totals)
                    .idempotentReplay(true)
                    .build();
        }

        SpaceEntity space = validateSetupRequest(spaceId, callerId, request);
        if (buildingRepository.existsBySpaceIdAndNameAndIsActiveTrue(spaceId, request.getBuilding().getName())) {
            throw new BusinessException("An active building with this name already exists in the space");
        }

        UserEntity caller = userRepository.findByIdAndIsActiveTrue(callerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", callerId));

        PropertyLayoutMode layoutMode = resolveLayoutMode(space, request);

        BuildingEntity building = buildingRepository.save(BuildingEntity.builder()
                .space(space)
                .name(request.getBuilding().getName())
                .code(request.getBuilding().getCode())
                .layoutMode(layoutMode)
                .build());

        SetupComputation computation = applySetup(space.getType(), layoutMode, request, building, false);
        AccommodationSetupTotals totals = computation.totals();

        try {
            idempotencyRepository.save(AccommodationSetupIdempotencyEntity.builder()
                    .space(space)
                    .idempotencyKey(idempotencyKey)
                    .building(building)
                    .totalsJson(serializeTotals(totals))
                    .createdBy(caller)
                    .build());
        } catch (DataIntegrityViolationException ex) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            var replay = idempotencyRepository.findBySpaceIdAndIdempotencyKey(spaceId, idempotencyKey)
                    .orElseThrow(() -> ex);
            return AccommodationSetupResultResponse.builder()
                    .buildingId(replay.getBuilding().getId())
                    .totals(deserializeTotals(replay.getTotalsJson()))
                    .idempotentReplay(true)
                    .build();
        }

        return AccommodationSetupResultResponse.builder()
                .buildingId(building.getId())
                .totals(totals)
                .idempotentReplay(false)
                .build();
    }

    private SpaceEntity validateSetupRequest(UUID spaceId, UUID callerId, AccommodationSetupRequest request) {
        SpaceEntity space = accessService.loadAccommodationSpace(spaceId);
        accessService.assertCanManageStructure(spaceId, callerId);

        if (request.getSpaceType() != space.getType()) {
            throw new BusinessException(
                    "Request spaceType does not match space type", HttpStatus.BAD_REQUEST);
        }

        PropertyLayoutMode layoutMode = resolveLayoutMode(space, request);
        layoutModeResolver.assertLayoutCompatibleWithSpaceType(space.getType(), layoutMode);

        switch (space.getType()) {
            case PG, HOSTEL -> {
                profileService.assertFloorsAllowed(spaceId);
                profileService.assertBedsAllowed(spaceId);
                if (request.getFloors() == null) {
                    throw new BusinessException("floors configuration is required for PG/Hostel setup");
                }
                if (request.getUnits() != null) {
                    throw new BusinessException("Only floors configuration is allowed for PG/Hostel setup");
                }
                if (layoutMode == PropertyLayoutMode.APARTMENT_PG
                        && (request.getFloors().getApartmentsPerFloor() == null
                                || request.getFloors().getApartmentsPerFloor() < 1)) {
                    throw new BusinessException(
                            "apartmentsPerFloor is required for apartment PG quick setup");
                }
            }
            case CO_LIVING -> {
                profileService.assertUnitsAllowed(spaceId);
                profileService.assertRoomsUnderUnitAllowed(spaceId);
                profileService.assertBedsAllowed(spaceId);
                validateCoLivingUnits(request.getUnits());
                if (request.getFloors() != null) {
                    throw new BusinessException("Only units configuration is allowed for Co-Living setup");
                }
            }
            case RENTAL -> {
                profileService.assertUnitsAllowed(spaceId);
                if (request.getUnits() == null) {
                    throw new BusinessException("units configuration is required for Rental setup");
                }
                if (request.getFloors() != null) {
                    throw new BusinessException("Only units configuration is allowed for Rental setup");
                }
            }
            default -> throw new BusinessException("Quick setup is not supported for this space type");
        }
        return space;
    }

    private PropertyLayoutMode resolveLayoutMode(SpaceEntity space, AccommodationSetupRequest request) {
        PropertyLayoutMode layoutMode = request.getLayoutMode() != null
                ? request.getLayoutMode()
                : layoutModeResolver.defaultForSpaceType(space.getType());
        layoutModeResolver.assertLayoutCompatibleWithSpaceType(space.getType(), layoutMode);
        return layoutMode;
    }

    private SetupComputation applySetup(
            SpaceType spaceType,
            PropertyLayoutMode layoutMode,
            AccommodationSetupRequest request,
            BuildingEntity building,
            boolean dryRun) {
        return switch (spaceType) {
            case PG, HOSTEL -> applyPgHostelSetup(request.getFloors(), layoutMode, building, dryRun);
            case CO_LIVING -> applyCoLivingSetup(request.getUnits(), building, dryRun);
            case RENTAL -> applyRentalSetup(request.getUnits(), building, dryRun);
            default -> throw new BusinessException("Quick setup is not supported for this space type");
        };
    }

    private SetupComputation applyPgHostelSetup(
            PgHostelSetupConfig config,
            PropertyLayoutMode layoutMode,
            BuildingEntity building,
            boolean dryRun) {
        if (layoutMode == PropertyLayoutMode.APARTMENT_PG) {
            return applyApartmentPgSetup(config, building, dryRun);
        }
        return applyCorridorPgSetup(config, building, dryRun);
    }

    private SetupComputation applyCorridorPgSetup(
            PgHostelSetupConfig config, BuildingEntity building, boolean dryRun) {
        int floors = config.getCount();
        int rooms = floors * config.getRoomsPerFloor();
        int beds = rooms * config.getBedsPerRoom();
        validateLimits(floors, rooms, rooms, beds);

        boolean includeGround = config.isIncludeGroundFloor();
        List<AccommodationSetupSampleNode> sample = new ArrayList<>();
        AccommodationSetupSampleNode sampleFloor = null;

        for (int floorIndex = 0; floorIndex < config.getCount(); floorIndex++) {
            int floorNumber = numberingService.floorNumberForIndex(floorIndex, includeGround);
            FloorEntity floor = null;
            if (!dryRun) {
                floor = floorRepository.save(FloorEntity.builder()
                        .building(building)
                        .name(numberingService.floorDisplayName(floorNumber, includeGround))
                        .floorNumber(floorNumber)
                        .sortOrder(floorNumber)
                        .build());
            }

            List<AccommodationSetupSampleNode> sampleRooms = new ArrayList<>();
            for (int roomIndex = 1; roomIndex <= config.getRoomsPerFloor(); roomIndex++) {
                String roomNumber = numberingService.pgRoomNumber(floorIndex, roomIndex);
                RoomEntity room = null;
                if (!dryRun) {
                    UnitEntity syntheticUnit = unitRepository.save(UnitEntity.builder()
                            .building(building)
                            .floor(floor)
                            .name(numberingService.unitDisplayName(roomNumber))
                            .unitNumber(roomNumber)
                            .status(AccommodationStatus.AVAILABLE)
                            .synthetic(true)
                            .build());
                    room = roomRepository.save(RoomEntity.builder()
                            .unit(syntheticUnit)
                            .name(numberingService.roomDisplayName(roomNumber))
                            .roomNumber(roomNumber)
                            .roomType(config.getDefaultRoomType())
                            .capacity(config.getCapacityPerRoom())
                            .status(AccommodationStatus.AVAILABLE)
                            .build());
                    createBedsForRoom(room, config.getBedsPerRoom(), BedLabelStyle.ALPHA);
                } else if (floorIndex == 0 && roomIndex <= Math.min(2, config.getRoomsPerFloor())) {
                    List<AccommodationSetupSampleNode> bedNodes = buildSampleBedNodes(
                            Math.min(3, config.getBedsPerRoom()));
                    sampleRooms.add(AccommodationSetupSampleNode.builder()
                            .type("ROOM")
                            .label(numberingService.roomDisplayName(roomNumber))
                            .number(roomNumber)
                            .children(bedNodes)
                            .build());
                }
            }

            if (dryRun && floorIndex == 0) {
                sampleFloor = AccommodationSetupSampleNode.builder()
                        .type("FLOOR")
                        .label(numberingService.floorDisplayName(floorNumber, includeGround))
                        .number(String.valueOf(floorNumber))
                        .children(sampleRooms)
                        .build();
            }
        }

        if (dryRun && sampleFloor != null) {
            sample.add(sampleFloor);
        }

        return new SetupComputation(
                AccommodationSetupTotals.builder()
                        .floors(floors)
                        .units(rooms)
                        .rooms(rooms)
                        .beds(beds)
                        .build(),
                sample,
                buildWarnings(beds));
    }

    private SetupComputation applyApartmentPgSetup(
            PgHostelSetupConfig config, BuildingEntity building, boolean dryRun) {
        int floors = config.getCount();
        int apartmentsPerFloor = config.getApartmentsPerFloor();
        int roomsPerApartment = config.getRoomsPerFloor();
        int units = floors * apartmentsPerFloor;
        int rooms = units * roomsPerApartment;
        int beds = rooms * config.getBedsPerRoom();
        validateLimits(floors, units, rooms, beds);

        boolean includeGround = config.isIncludeGroundFloor();
        List<AccommodationSetupSampleNode> sample = new ArrayList<>();
        AccommodationSetupSampleNode sampleFloor = null;

        for (int floorIndex = 0; floorIndex < config.getCount(); floorIndex++) {
            int floorNumber = numberingService.floorNumberForIndex(floorIndex, includeGround);
            FloorEntity floor = null;
            if (!dryRun) {
                floor = floorRepository.save(FloorEntity.builder()
                        .building(building)
                        .name(numberingService.floorDisplayName(floorNumber, includeGround))
                        .floorNumber(floorNumber)
                        .sortOrder(floorNumber)
                        .build());
            }

            List<String> unitNumbers = numberingService.nextUnitNumbers(
                    String.valueOf((floorIndex + 1) * 100 + 1), 1, apartmentsPerFloor);
            List<AccommodationSetupSampleNode> sampleUnits = new ArrayList<>();

            for (int unitIndex = 0; unitIndex < unitNumbers.size(); unitIndex++) {
                String unitNumber = unitNumbers.get(unitIndex);
                UnitEntity unit = null;
                if (!dryRun) {
                    unit = unitRepository.save(UnitEntity.builder()
                            .building(building)
                            .floor(floor)
                            .name(numberingService.unitDisplayName(unitNumber))
                            .unitNumber(unitNumber)
                            .status(AccommodationStatus.AVAILABLE)
                            .synthetic(false)
                            .build());
                }

                List<String> roomLabels = numberingService.coLivingRoomLabels(roomsPerApartment);
                List<AccommodationSetupSampleNode> sampleRooms = new ArrayList<>();
                for (int roomIndex = 0; roomIndex < roomLabels.size(); roomIndex++) {
                    String roomLabel = roomLabels.get(roomIndex);
                    if (!dryRun) {
                        RoomEntity room = roomRepository.save(RoomEntity.builder()
                                .unit(unit)
                                .name(numberingService.roomDisplayName(roomLabel))
                                .roomNumber(roomLabel)
                                .roomType(config.getDefaultRoomType())
                                .capacity(config.getCapacityPerRoom())
                                .status(AccommodationStatus.AVAILABLE)
                                .build());
                        createBedsForRoom(room, config.getBedsPerRoom(), BedLabelStyle.ALPHA);
                    } else if (floorIndex == 0 && unitIndex == 0 && roomIndex < Math.min(2, roomLabels.size())) {
                        List<AccommodationSetupSampleNode> bedNodes = buildSampleBedNodes(
                                Math.min(3, config.getBedsPerRoom()));
                        sampleRooms.add(AccommodationSetupSampleNode.builder()
                                .type("ROOM")
                                .label(numberingService.roomDisplayName(roomLabel))
                                .number(roomLabel)
                                .children(bedNodes)
                                .build());
                    }
                }

                if (dryRun && floorIndex == 0 && unitIndex == 0) {
                    sampleUnits.add(AccommodationSetupSampleNode.builder()
                            .type("UNIT")
                            .label(numberingService.unitDisplayName(unitNumber))
                            .number(unitNumber)
                            .children(sampleRooms)
                            .build());
                }
            }

            if (dryRun && floorIndex == 0 && !sampleUnits.isEmpty()) {
                sampleFloor = AccommodationSetupSampleNode.builder()
                        .type("FLOOR")
                        .label(numberingService.floorDisplayName(floorNumber, includeGround))
                        .number(String.valueOf(floorNumber))
                        .children(sampleUnits)
                        .build();
            }
        }

        if (dryRun && sampleFloor != null) {
            sample.add(sampleFloor);
        }

        return new SetupComputation(
                AccommodationSetupTotals.builder()
                        .floors(floors)
                        .units(units)
                        .rooms(rooms)
                        .beds(beds)
                        .build(),
                sample,
                buildWarnings(beds));
    }

    private SetupComputation applyCoLivingSetup(
            UnitSetupConfig config, BuildingEntity building, boolean dryRun) {
        validateCoLivingUnits(config);
        int units = config.getCount();
        int rooms = units * config.getRoomsPerUnit();
        int beds = rooms * config.getBedsPerRoom();
        validateLimits(0, units, rooms, beds);

        List<String> unitNumbers = numberingService.nextUnitNumbers(
                config.resolvedStartNumber(), config.resolvedNumberingStep(), config.getCount());
        List<AccommodationSetupSampleNode> sample = new ArrayList<>();
        AccommodationSetupSampleNode sampleUnit = null;

        for (int unitIndex = 0; unitIndex < unitNumbers.size(); unitIndex++) {
            String unitNumber = unitNumbers.get(unitIndex);
            UnitEntity unit = null;
            if (!dryRun) {
                unit = unitRepository.save(UnitEntity.builder()
                        .building(building)
                        .name(numberingService.unitDisplayName(unitNumber))
                        .unitNumber(unitNumber)
                        .status(AccommodationStatus.AVAILABLE)
                        .build());
            }

            List<String> roomLabels = numberingService.coLivingRoomLabels(config.getRoomsPerUnit());
            List<AccommodationSetupSampleNode> sampleRooms = new ArrayList<>();
            for (int roomIndex = 0; roomIndex < roomLabels.size(); roomIndex++) {
                String roomLabel = roomLabels.get(roomIndex);
                if (!dryRun) {
                    RoomEntity room = roomRepository.save(RoomEntity.builder()
                            .unit(unit)
                            .name(numberingService.roomDisplayName(roomLabel))
                            .roomNumber(roomLabel)
                            .roomType(config.getDefaultRoomType())
                            .capacity(config.getCapacityPerRoom())
                            .status(AccommodationStatus.AVAILABLE)
                            .build());
                    createBedsForRoom(room, config.getBedsPerRoom(), BedLabelStyle.ALPHA);
                } else if (unitIndex == 0 && roomIndex < Math.min(2, roomLabels.size())) {
                    List<AccommodationSetupSampleNode> bedNodes = buildSampleBedNodes(
                            Math.min(2, config.getBedsPerRoom()));
                    sampleRooms.add(AccommodationSetupSampleNode.builder()
                            .type("ROOM")
                            .label(numberingService.roomDisplayName(roomLabel))
                            .number(roomLabel)
                            .children(bedNodes)
                            .build());
                }
            }

            if (dryRun && unitIndex == 0) {
                sampleUnit = AccommodationSetupSampleNode.builder()
                        .type("UNIT")
                        .label(numberingService.unitDisplayName(unitNumber))
                        .number(unitNumber)
                        .children(sampleRooms)
                        .build();
            }
        }

        if (dryRun && sampleUnit != null) {
            sample.add(sampleUnit);
        }

        return new SetupComputation(
                AccommodationSetupTotals.builder()
                        .floors(0)
                        .units(units)
                        .rooms(rooms)
                        .beds(beds)
                        .build(),
                sample,
                buildWarnings(beds));
    }

    private SetupComputation applyRentalSetup(UnitSetupConfig config, BuildingEntity building, boolean dryRun) {
        if (config == null) {
            throw new BusinessException("units configuration is required for Rental setup");
        }
        int units = config.getCount();
        validateLimits(0, units, 0, 0);

        List<String> unitNumbers = numberingService.nextUnitNumbers(
                config.resolvedStartNumber(), 1, config.getCount());
        AccommodationStatus status = config.resolvedDefaultStatus();
        List<AccommodationSetupSampleNode> sample = new ArrayList<>();

        for (int unitIndex = 0; unitIndex < unitNumbers.size(); unitIndex++) {
            String unitNumber = unitNumbers.get(unitIndex);
            if (!dryRun) {
                unitRepository.save(UnitEntity.builder()
                        .building(building)
                        .name(numberingService.unitDisplayName(unitNumber))
                        .unitNumber(unitNumber)
                        .status(status)
                        .build());
            } else if (unitIndex == 0) {
                sample.add(AccommodationSetupSampleNode.builder()
                        .type("UNIT")
                        .label(numberingService.unitDisplayName(unitNumber))
                        .number(unitNumber)
                        .children(List.of())
                        .build());
            }
        }

        return new SetupComputation(
                AccommodationSetupTotals.builder()
                        .floors(0)
                        .units(units)
                        .rooms(0)
                        .beds(0)
                        .build(),
                sample,
                List.of());
    }

    private List<AccommodationSetupSampleNode> buildSampleBedNodes(int count) {
        List<AccommodationSetupSampleNode> bedNodes = new ArrayList<>();
        List<String> labels = numberingService.bedLabels(count, BedLabelStyle.ALPHA, new HashSet<>());
        for (String label : labels) {
            bedNodes.add(AccommodationSetupSampleNode.builder()
                    .type("BED")
                    .label(numberingService.bedDisplayName(label))
                    .number(label)
                    .build());
        }
        return bedNodes;
    }

    private void validateCoLivingUnits(UnitSetupConfig config) {
        if (config == null) {
            throw new BusinessException("units configuration is required for Co-Living setup");
        }
        if (config.getRoomsPerUnit() == null || config.getBedsPerRoom() == null
                || config.getDefaultRoomType() == null || config.getCapacityPerRoom() == null) {
            throw new BusinessException(
                    "roomsPerUnit, bedsPerRoom, defaultRoomType, and capacityPerRoom are required for Co-Living");
        }
    }

    private void validateLimits(int floors, int units, int rooms, int beds) {
        if (floors > AccommodationLimits.MAX_FLOORS_PER_SETUP) {
            throw new BusinessException(
                    "Setup exceeds maximum of " + AccommodationLimits.MAX_FLOORS_PER_SETUP + " floors");
        }
        if (units > AccommodationLimits.MAX_UNITS_PER_SETUP) {
            throw new BusinessException(
                    "Setup exceeds maximum of " + AccommodationLimits.MAX_UNITS_PER_SETUP + " units");
        }
        if (beds > AccommodationLimits.MAX_BEDS_PER_SETUP) {
            throw new BusinessException(
                    "Setup exceeds maximum of " + AccommodationLimits.MAX_BEDS_PER_SETUP + " beds");
        }
    }

    private List<String> buildWarnings(int beds) {
        List<String> warnings = new ArrayList<>();
        if (beds >= AccommodationLimits.WARNING_BED_THRESHOLD
                && beds <= AccommodationLimits.MAX_BEDS_PER_SETUP) {
            warnings.add("Approaching the " + AccommodationLimits.MAX_BEDS_PER_SETUP + "-bed limit per setup");
        }
        return warnings;
    }

    private void createBedsForRoom(RoomEntity room, int count, BedLabelStyle style) {
        Set<String> existing = new HashSet<>();
        List<String> labels = numberingService.bedLabels(count, style, existing);
        List<BedEntity> beds = new ArrayList<>(labels.size());
        for (String label : labels) {
            beds.add(BedEntity.builder()
                    .room(room)
                    .name(numberingService.bedDisplayName(label))
                    .bedNumber(label)
                    .status(AccommodationStatus.AVAILABLE)
                    .build());
        }
        bedRepository.saveAll(beds);
    }

    private String serializeTotals(AccommodationSetupTotals totals) {
        try {
            return objectMapper.writeValueAsString(totals);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize setup totals", ex);
        }
    }

    private AccommodationSetupTotals deserializeTotals(String json) {
        if (json == null || json.isBlank()) {
            return AccommodationSetupTotals.builder().build();
        }
        try {
            return objectMapper.readValue(json, AccommodationSetupTotals.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize setup totals", ex);
        }
    }

    private record SetupComputation(
            AccommodationSetupTotals totals,
            List<AccommodationSetupSampleNode> sample,
            List<String> warnings) {}
}
