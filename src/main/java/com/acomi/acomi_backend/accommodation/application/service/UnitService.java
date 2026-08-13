package com.acomi.acomi_backend.accommodation.application.service;

import com.acomi.acomi_backend.accommodation.api.dto.request.CreateUnitRequest;
import com.acomi.acomi_backend.accommodation.api.dto.request.UpdateUnitRequest;
import com.acomi.acomi_backend.accommodation.api.dto.response.UnitResponse;
import com.acomi.acomi_backend.accommodation.domain.model.AccommodationStatus;
import com.acomi.acomi_backend.accommodation.domain.model.PropertyLayoutMode;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.FloorEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.UnitEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.BuildingRepository;
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
public class UnitService {

    private final UnitRepository unitRepository;
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final RoomRepository roomRepository;
    private final AccommodationAccessService accessService;
    private final AccommodationProfileService profileService;
    private final AccommodationActionService actionService;
    private final AccommodationLayoutService layoutService;

    @Transactional
    public UnitResponse createUnit(
            UUID spaceId, UUID buildingId, UUID callerId, CreateUnitRequest request) {
        log.info("Creating unit: spaceId={}, buildingId={}, callerId={}, unitNumber={}",
                spaceId, buildingId, callerId, request.getUnitNumber());

        accessService.assertCanManageStructure(spaceId, callerId);

        BuildingEntity building = buildingRepository.findActiveByIdAndSpaceId(buildingId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Building", "id", buildingId));

        layoutService.assertVisibleUnitCreationAllowed(building);
        assertUnitsAllowedForLayout(building.getLayoutMode());

        return UnitResponse.from(saveVisibleUnit(building, null, request));
    }

    @Transactional
    public UnitResponse createUnitUnderFloor(
            UUID spaceId, UUID buildingId, UUID floorId, UUID callerId, CreateUnitRequest request) {
        log.info("Creating unit under floor: spaceId={}, buildingId={}, floorId={}, callerId={}",
                spaceId, buildingId, floorId, callerId);

        accessService.assertCanManageStructure(spaceId, callerId);

        BuildingEntity building = buildingRepository.findActiveByIdAndSpaceId(buildingId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Building", "id", buildingId));

        layoutService.assertFloorScopedUnitCreationAllowed(building);

        FloorEntity floor = floorRepository.findActiveByIdAndBuildingId(floorId, buildingId)
                .orElseThrow(() -> new ResourceNotFoundException("Floor", "id", floorId));

        if (unitRepository.existsByFloorIdAndUnitNumberAndIsActiveTrue(floorId, request.getUnitNumber())) {
            throw new BusinessException("An active apartment with this number already exists on the floor");
        }

        return UnitResponse.from(saveVisibleUnit(building, floor, request));
    }

    @Transactional(readOnly = true)
    public List<UnitResponse> getUnits(UUID spaceId, UUID buildingId, UUID callerId, boolean includeSynthetic) {
        log.info("Listing units: spaceId={}, buildingId={}, callerId={}", spaceId, buildingId, callerId);

        accessService.assertCanViewStructure(spaceId, callerId);
        assertBuildingInSpace(spaceId, buildingId);

        return unitRepository.findActiveByBuildingId(buildingId, includeSynthetic)
                .stream()
                .map(UnitResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UnitResponse> getUnitsByFloor(
            UUID spaceId, UUID buildingId, UUID floorId, UUID callerId, boolean includeSynthetic) {
        accessService.assertCanViewStructure(spaceId, callerId);
        assertBuildingInSpace(spaceId, buildingId);
        floorRepository.findActiveByIdAndBuildingId(floorId, buildingId)
                .orElseThrow(() -> new ResourceNotFoundException("Floor", "id", floorId));

        return unitRepository.findActiveByFloorId(floorId, includeSynthetic)
                .stream()
                .map(UnitResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UnitResponse getUnit(UUID spaceId, UUID buildingId, UUID unitId, UUID callerId) {
        log.info("Fetching unit: spaceId={}, buildingId={}, unitId={}, callerId={}",
                spaceId, buildingId, unitId, callerId);

        accessService.assertCanViewStructure(spaceId, callerId);

        UnitEntity unit = unitRepository.findActiveByIdAndBuildingId(unitId, buildingId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit", "id", unitId));
        assertBuildingInSpace(spaceId, buildingId);
        layoutService.assertUnitVisible(unit);

        return UnitResponse.from(unit, actionService.forUnit(spaceId, unit, callerId));
    }

    @Transactional(readOnly = true)
    public UnitResponse getUnitById(UUID spaceId, UUID unitId, UUID callerId) {
        accessService.assertCanViewStructure(spaceId, callerId);

        UnitEntity unit = unitRepository.findByIdAndSpaceId(unitId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit", "id", unitId));
        layoutService.assertUnitVisible(unit);

        return UnitResponse.from(unit, actionService.forUnit(spaceId, unit, callerId));
    }

    @Transactional
    public UnitResponse updateUnit(
            UUID spaceId, UUID buildingId, UUID unitId, UUID callerId, UpdateUnitRequest request) {
        return updateUnitEntity(
                spaceId,
                unitRepository.findActiveByIdAndBuildingId(unitId, buildingId)
                        .orElseThrow(() -> new ResourceNotFoundException("Unit", "id", unitId)),
                callerId,
                request);
    }

    @Transactional
    public UnitResponse updateUnitById(UUID spaceId, UUID unitId, UUID callerId, UpdateUnitRequest request) {
        UnitEntity unit = unitRepository.findByIdAndSpaceId(unitId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit", "id", unitId));
        return updateUnitEntity(spaceId, unit, callerId, request);
    }

    @Transactional
    public void deactivateUnitById(UUID spaceId, UUID unitId, UUID callerId) {
        UnitEntity unit = unitRepository.findByIdAndSpaceId(unitId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit", "id", unitId));
        deactivateUnit(spaceId, unit.getBuilding().getId(), unitId, callerId);
    }

    @Transactional
    public void deactivateUnit(UUID spaceId, UUID buildingId, UUID unitId, UUID callerId) {
        log.info("Deactivating unit: spaceId={}, buildingId={}, unitId={}, callerId={}",
                spaceId, buildingId, unitId, callerId);

        accessService.assertCanDeactivateStructure(spaceId, callerId);

        UnitEntity unit = unitRepository.findActiveByIdAndBuildingId(unitId, buildingId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit", "id", unitId));
        assertBuildingInSpace(spaceId, buildingId);
        layoutService.assertUnitVisible(unit);

        if (roomRepository.existsByUnitIdAndIsActiveTrue(unitId)) {
            throw new BusinessException(
                    "Cannot deactivate unit while active rooms exist. Deactivate rooms first.");
        }

        unit.setActive(false);
        unitRepository.save(unit);
    }

    private UnitResponse updateUnitEntity(
            UUID spaceId, UnitEntity unit, UUID callerId, UpdateUnitRequest request) {
        log.info("Updating unit: spaceId={}, unitId={}, callerId={}", spaceId, unit.getId(), callerId);

        accessService.assertCanManageStructure(spaceId, callerId);
        layoutService.assertUnitUpdateAllowed(unit);

        UUID buildingId = unit.getBuilding().getId();
        if (!unit.getUnitNumber().equals(request.getUnitNumber())
                && unitRepository.existsByBuildingIdAndUnitNumberAndIsActiveTrue(
                        buildingId, request.getUnitNumber())) {
            throw new BusinessException("An active unit with this unit number already exists in the building");
        }

        unit.setName(request.getName());
        unit.setUnitNumber(request.getUnitNumber());
        unit.setStatus(request.getStatus());
        unit.setUnitKind(request.getUnitKind());
        unit.setDefaultRent(request.getDefaultRent());
        unit.setDefaultDeposit(request.getDefaultDeposit());

        return UnitResponse.from(unitRepository.save(unit));
    }

    private UnitEntity saveVisibleUnit(BuildingEntity building, FloorEntity floor, CreateUnitRequest request) {
        if (unitRepository.existsByBuildingIdAndUnitNumberAndIsActiveTrue(
                building.getId(), request.getUnitNumber())) {
            throw new BusinessException("An active unit with this unit number already exists in the building");
        }

        AccommodationStatus status = request.getStatus() != null
                ? request.getStatus()
                : AccommodationStatus.AVAILABLE;

        UnitEntity unit = UnitEntity.builder()
                .building(building)
                .floor(floor)
                .name(request.getName())
                .unitNumber(request.getUnitNumber())
                .status(status)
                .unitKind(request.getUnitKind())
                .synthetic(false)
                .build();

        return unitRepository.save(unit);
    }

    private void assertUnitsAllowedForLayout(PropertyLayoutMode layoutMode) {
        if (layoutMode == PropertyLayoutMode.CORRIDOR_PG) {
            throw new BusinessException("Visible apartments are not supported in corridor PG layout");
        }
    }

    private void assertBuildingInSpace(UUID spaceId, UUID buildingId) {
        buildingRepository.findActiveByIdAndSpaceId(buildingId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Building", "id", buildingId));
    }
}
