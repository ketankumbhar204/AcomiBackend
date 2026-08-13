package com.acomi.acomi_backend.accommodation.application.service;

import com.acomi.acomi_backend.accommodation.api.dto.request.CreateFloorRequest;
import com.acomi.acomi_backend.accommodation.api.dto.request.UpdateFloorRequest;
import com.acomi.acomi_backend.accommodation.api.dto.response.FloorResponse;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.FloorEntity;
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
public class FloorService {

    private final FloorRepository floorRepository;
    private final BuildingRepository buildingRepository;
    private final RoomRepository roomRepository;
    private final UnitRepository unitRepository;
    private final AccommodationAccessService accessService;
    private final AccommodationProfileService profileService;
    private final AccommodationActionService actionService;

    @Transactional
    public FloorResponse createFloor(
            UUID spaceId, UUID buildingId, UUID callerId, CreateFloorRequest request) {
        log.info("Creating floor: spaceId={}, buildingId={}, callerId={}, name={}",
                spaceId, buildingId, callerId, request.getName());

        profileService.assertFloorsAllowed(spaceId);
        accessService.assertCanManageStructure(spaceId, callerId);

        BuildingEntity building = buildingRepository.findActiveByIdAndSpaceId(buildingId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Building", "id", buildingId));

        if (floorRepository.existsByBuildingIdAndFloorNumberAndIsActiveTrue(
                buildingId, request.getFloorNumber())) {
            throw new BusinessException("An active floor with this floor number already exists in the building");
        }

        int sortOrder = request.getSortOrder() != null ? request.getSortOrder() : request.getFloorNumber();

        FloorEntity floor = FloorEntity.builder()
                .building(building)
                .name(request.getName())
                .floorNumber(request.getFloorNumber())
                .sortOrder(sortOrder)
                .build();

        floor = floorRepository.save(floor);
        return FloorResponse.from(floor);
    }

    @Transactional(readOnly = true)
    public List<FloorResponse> getFloors(UUID spaceId, UUID buildingId, UUID callerId) {
        log.info("Listing floors: spaceId={}, buildingId={}, callerId={}", spaceId, buildingId, callerId);

        accessService.assertCanViewStructure(spaceId, callerId);
        assertBuildingInSpace(spaceId, buildingId);

        return floorRepository.findActiveByBuildingId(buildingId)
                .stream()
                .map(FloorResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public FloorResponse getFloor(UUID spaceId, UUID buildingId, UUID floorId, UUID callerId) {
        log.info("Fetching floor: spaceId={}, buildingId={}, floorId={}, callerId={}",
                spaceId, buildingId, floorId, callerId);

        accessService.assertCanViewStructure(spaceId, callerId);

        FloorEntity floor = floorRepository.findActiveByIdAndBuildingId(floorId, buildingId)
                .orElseThrow(() -> new ResourceNotFoundException("Floor", "id", floorId));
        assertBuildingInSpace(spaceId, buildingId);

        return FloorResponse.from(floor, actionService.forFloor(spaceId, floor, callerId));
    }

    @Transactional(readOnly = true)
    public FloorResponse getFloorById(UUID spaceId, UUID floorId, UUID callerId) {
        accessService.assertCanViewStructure(spaceId, callerId);

        FloorEntity floor = floorRepository.findByIdAndSpaceId(floorId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Floor", "id", floorId));

        return FloorResponse.from(floor, actionService.forFloor(spaceId, floor, callerId));
    }

    @Transactional
    public FloorResponse updateFloor(
            UUID spaceId, UUID buildingId, UUID floorId, UUID callerId, UpdateFloorRequest request) {
        log.info("Updating floor: spaceId={}, buildingId={}, floorId={}, callerId={}",
                spaceId, buildingId, floorId, callerId);

        profileService.assertFloorsAllowed(spaceId);
        accessService.assertCanManageStructure(spaceId, callerId);

        FloorEntity floor = floorRepository.findActiveByIdAndBuildingId(floorId, buildingId)
                .orElseThrow(() -> new ResourceNotFoundException("Floor", "id", floorId));
        assertBuildingInSpace(spaceId, buildingId);

        if (floor.getFloorNumber() != request.getFloorNumber()
                && floorRepository.existsByBuildingIdAndFloorNumberAndIsActiveTrue(
                        buildingId, request.getFloorNumber())) {
            throw new BusinessException("An active floor with this floor number already exists in the building");
        }

        floor.setName(request.getName());
        floor.setFloorNumber(request.getFloorNumber());
        floor.setSortOrder(request.getSortOrder());

        return FloorResponse.from(floorRepository.save(floor));
    }

    @Transactional
    public void deactivateFloorById(UUID spaceId, UUID floorId, UUID callerId) {
        FloorEntity floor = floorRepository.findByIdAndSpaceId(floorId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Floor", "id", floorId));
        deactivateFloor(spaceId, floor.getBuilding().getId(), floorId, callerId);
    }

    @Transactional
    public void deactivateFloor(UUID spaceId, UUID buildingId, UUID floorId, UUID callerId) {
        log.info("Deactivating floor: spaceId={}, buildingId={}, floorId={}, callerId={}",
                spaceId, buildingId, floorId, callerId);

        accessService.assertCanDeactivateStructure(spaceId, callerId);

        FloorEntity floor = floorRepository.findActiveByIdAndBuildingId(floorId, buildingId)
                .orElseThrow(() -> new ResourceNotFoundException("Floor", "id", floorId));
        assertBuildingInSpace(spaceId, buildingId);

        if (unitRepository.existsByFloorIdAndIsActiveTrue(floorId)) {
            throw new BusinessException(
                    "Cannot deactivate floor while active apartments exist. Deactivate apartments first.");
        }

        if (roomRepository.existsByFloorIdAndIsActiveTrue(floorId)) {
            throw new BusinessException(
                    "Cannot deactivate floor while active rooms exist. Deactivate rooms first.");
        }

        floor.setActive(false);
        floorRepository.save(floor);
    }

    private void assertBuildingInSpace(UUID spaceId, UUID buildingId) {
        buildingRepository.findActiveByIdAndSpaceId(buildingId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Building", "id", buildingId));
    }
}
