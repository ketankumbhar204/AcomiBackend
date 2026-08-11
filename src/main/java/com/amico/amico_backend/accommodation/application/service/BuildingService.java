package com.amico.amico_backend.accommodation.application.service;

import com.amico.amico_backend.accommodation.api.dto.request.CreateBuildingRequest;
import com.amico.amico_backend.accommodation.api.dto.request.UpdateBuildingRequest;
import com.amico.amico_backend.accommodation.api.dto.response.BuildingResponse;
import com.amico.amico_backend.accommodation.domain.model.PropertyLayoutMode;
import com.amico.amico_backend.accommodation.domain.policy.PropertyLayoutModeResolver;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.repository.BuildingRepository;
import com.amico.amico_backend.accommodation.infrastructure.persistence.repository.FloorRepository;
import com.amico.amico_backend.accommodation.infrastructure.persistence.repository.UnitRepository;
import com.amico.amico_backend.common.exception.BusinessException;
import com.amico.amico_backend.common.exception.ResourceNotFoundException;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BuildingService {

    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final UnitRepository unitRepository;
    private final AccommodationAccessService accessService;
    private final AccommodationActionService actionService;
    private final PropertyLayoutModeResolver layoutModeResolver;

    @Transactional
    public BuildingResponse createBuilding(UUID spaceId, UUID callerId, CreateBuildingRequest request) {
        log.info("Creating building: spaceId={}, callerId={}, name={}", spaceId, callerId, request.getName());

        SpaceEntity space = accessService.loadAccommodationSpace(spaceId);
        accessService.assertCanManageStructure(spaceId, callerId);

        if (buildingRepository.existsBySpaceIdAndNameAndIsActiveTrue(spaceId, request.getName())) {
            throw new BusinessException("An active building with this name already exists in the space");
        }

        PropertyLayoutMode layoutMode = request.getLayoutMode() != null
                ? request.getLayoutMode()
                : layoutModeResolver.defaultForSpaceType(space.getType());
        layoutModeResolver.assertLayoutCompatibleWithSpaceType(space.getType(), layoutMode);

        BuildingEntity building = BuildingEntity.builder()
                .space(space)
                .name(request.getName())
                .code(request.getCode())
                .layoutMode(layoutMode)
                .build();

        building = buildingRepository.save(building);
        return BuildingResponse.from(building);
    }

    @Transactional(readOnly = true)
    public List<BuildingResponse> getBuildings(UUID spaceId, UUID callerId) {
        log.info("Listing buildings: spaceId={}, callerId={}", spaceId, callerId);

        accessService.assertCanViewStructure(spaceId, callerId);

        return buildingRepository.findActiveBySpaceId(spaceId)
                .stream()
                .map(BuildingResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public BuildingResponse getBuilding(UUID spaceId, UUID buildingId, UUID callerId) {
        log.info("Fetching building: spaceId={}, buildingId={}, callerId={}", spaceId, buildingId, callerId);

        accessService.assertCanViewStructure(spaceId, callerId);

        BuildingEntity building = buildingRepository.findActiveByIdAndSpaceId(buildingId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Building", "id", buildingId));

        return BuildingResponse.from(
                building, actionService.forBuilding(spaceId, building, callerId));
    }

    @Transactional
    public BuildingResponse updateBuilding(
            UUID spaceId, UUID buildingId, UUID callerId, UpdateBuildingRequest request) {
        log.info("Updating building: spaceId={}, buildingId={}, callerId={}", spaceId, buildingId, callerId);

        accessService.assertCanManageStructure(spaceId, callerId);

        BuildingEntity building = buildingRepository.findActiveByIdAndSpaceId(buildingId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Building", "id", buildingId));

        if (!building.getName().equals(request.getName())
                && buildingRepository.existsBySpaceIdAndNameAndIsActiveTrue(spaceId, request.getName())) {
            throw new BusinessException("An active building with this name already exists in the space");
        }

        building.setName(request.getName());
        building.setCode(request.getCode());
        if (request.getLayoutMode() != null) {
            layoutModeResolver.assertLayoutCompatibleWithSpaceType(
                    building.getSpace().getType(), request.getLayoutMode());
            building.setLayoutMode(request.getLayoutMode());
        }

        return BuildingResponse.from(buildingRepository.save(building));
    }

    @Transactional
    public void deactivateBuilding(UUID spaceId, UUID buildingId, UUID callerId) {
        log.info("Deactivating building: spaceId={}, buildingId={}, callerId={}",
                spaceId, buildingId, callerId);

        accessService.assertCanDeactivateStructure(spaceId, callerId);

        BuildingEntity building = buildingRepository.findActiveByIdAndSpaceId(buildingId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Building", "id", buildingId));

        if (floorRepository.existsByBuildingIdAndIsActiveTrue(buildingId)) {
            throw new BusinessException(
                    "Cannot deactivate building while active floors exist. Deactivate floors first.");
        }
        if (unitRepository.existsByBuildingIdAndIsActiveTrue(buildingId)) {
            throw new BusinessException(
                    "Cannot deactivate building while active units exist. Deactivate units first.");
        }

        building.setActive(false);
        buildingRepository.save(building);
    }
}
