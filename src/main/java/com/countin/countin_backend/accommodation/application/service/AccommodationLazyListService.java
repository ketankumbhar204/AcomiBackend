package com.countin.countin_backend.accommodation.application.service;

import com.countin.countin_backend.accommodation.api.dto.response.BedListItemResponse;
import com.countin.countin_backend.accommodation.api.dto.response.BedSpaceListItemResponse;
import com.countin.countin_backend.accommodation.api.dto.response.FloorListItemResponse;
import com.countin.countin_backend.accommodation.api.dto.response.RoomListItemResponse;
import com.countin.countin_backend.accommodation.api.dto.response.UnitListItemResponse;
import com.countin.countin_backend.accommodation.domain.model.AccommodationStatus;
import com.countin.countin_backend.accommodation.infrastructure.persistence.repository.AccommodationLazyListRepository;
import com.countin.countin_backend.accommodation.infrastructure.persistence.repository.BuildingRepository;
import com.countin.countin_backend.accommodation.infrastructure.persistence.repository.FloorRepository;
import com.countin.countin_backend.accommodation.infrastructure.persistence.repository.RoomRepository;
import com.countin.countin_backend.accommodation.infrastructure.persistence.repository.UnitRepository;
import com.countin.countin_backend.common.exception.ResourceNotFoundException;
import com.countin.countin_backend.common.web.PagedResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccommodationLazyListService {

    private final AccommodationAccessService accessService;
    private final AccommodationLazyListRepository lazyListRepository;
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final UnitRepository unitRepository;
    private final RoomRepository roomRepository;

    @Transactional(readOnly = true)
    public PagedResponse<FloorListItemResponse> listFloorsByBuilding(
            UUID spaceId, UUID buildingId, UUID callerId, String query, Pageable pageable) {
        log.info("Listing floors (lazy): spaceId={}, buildingId={}, callerId={}", spaceId, buildingId, callerId);

        accessService.assertCanViewStructure(spaceId, callerId);
        assertBuildingInSpace(spaceId, buildingId);

        Page<FloorListItemResponse> page = lazyListRepository.findFloorListItemsByBuildingId(
                buildingId, normalizeQuery(query), AccommodationStatus.AVAILABLE, AccommodationStatus.OCCUPIED, pageable);
        return PagedResponse.from(page);
    }

    @Transactional(readOnly = true)
    public PagedResponse<FloorListItemResponse> searchFloorsInSpace(
            UUID spaceId, UUID callerId, String query, Pageable pageable) {
        log.info("Searching floors (lazy): spaceId={}, callerId={}", spaceId, callerId);

        accessService.assertCanViewStructure(spaceId, callerId);

        Page<FloorListItemResponse> page = lazyListRepository.findFloorListItemsBySpaceId(
                spaceId, normalizeQuery(query), AccommodationStatus.AVAILABLE, AccommodationStatus.OCCUPIED, pageable);
        return PagedResponse.from(page);
    }

    @Transactional(readOnly = true)
    public PagedResponse<UnitListItemResponse> listUnitsByBuilding(
            UUID spaceId,
            UUID buildingId,
            UUID callerId,
            String query,
            boolean includeSynthetic,
            Pageable pageable) {
        log.info("Listing units (lazy): spaceId={}, buildingId={}, callerId={}", spaceId, buildingId, callerId);

        accessService.assertCanViewStructure(spaceId, callerId);
        assertBuildingInSpace(spaceId, buildingId);

        Page<UnitListItemResponse> page = lazyListRepository.findUnitListItemsByBuildingId(
                buildingId,
                normalizeQuery(query),
                includeSynthetic,
                AccommodationStatus.AVAILABLE,
                AccommodationStatus.OCCUPIED,
                pageable);
        return PagedResponse.from(page);
    }

    @Transactional(readOnly = true)
    public PagedResponse<UnitListItemResponse> listUnitsByFloor(
            UUID spaceId,
            UUID buildingId,
            UUID floorId,
            UUID callerId,
            String query,
            boolean includeSynthetic,
            Pageable pageable) {
        log.info("Listing units by floor (lazy): spaceId={}, floorId={}, callerId={}",
                spaceId, floorId, callerId);

        accessService.assertCanViewStructure(spaceId, callerId);
        assertBuildingInSpace(spaceId, buildingId);
        assertFloorInBuilding(buildingId, floorId);

        Page<UnitListItemResponse> page = lazyListRepository.findUnitListItemsByFloorId(
                floorId,
                normalizeQuery(query),
                includeSynthetic,
                AccommodationStatus.AVAILABLE,
                AccommodationStatus.OCCUPIED,
                pageable);
        return PagedResponse.from(page);
    }

    @Transactional(readOnly = true)
    public PagedResponse<UnitListItemResponse> searchUnitsInSpace(
            UUID spaceId, UUID callerId, String query, Pageable pageable) {
        log.info("Searching units (lazy): spaceId={}, callerId={}", spaceId, callerId);

        accessService.assertCanViewStructure(spaceId, callerId);

        Page<UnitListItemResponse> page = lazyListRepository.findUnitListItemsBySpaceId(
                spaceId,
                normalizeQuery(query),
                false,
                AccommodationStatus.AVAILABLE,
                AccommodationStatus.OCCUPIED,
                pageable);
        return PagedResponse.from(page);
    }

    @Transactional(readOnly = true)
    public PagedResponse<RoomListItemResponse> listRoomsByFloor(
            UUID spaceId, UUID floorId, UUID callerId, String query, Pageable pageable) {
        log.info("Listing rooms by floor (lazy): spaceId={}, floorId={}, callerId={}", spaceId, floorId, callerId);

        accessService.assertCanViewStructure(spaceId, callerId);
        assertFloorInSpace(spaceId, floorId);

        Page<RoomListItemResponse> page = lazyListRepository.findRoomListItemsByFloorId(
                floorId, normalizeQuery(query), AccommodationStatus.AVAILABLE, AccommodationStatus.OCCUPIED, pageable);
        return PagedResponse.from(page);
    }

    @Transactional(readOnly = true)
    public PagedResponse<RoomListItemResponse> listRoomsByUnit(
            UUID spaceId, UUID unitId, UUID callerId, String query, Pageable pageable) {
        log.info("Listing rooms by unit (lazy): spaceId={}, unitId={}, callerId={}", spaceId, unitId, callerId);

        accessService.assertCanViewStructure(spaceId, callerId);
        assertUnitInSpace(spaceId, unitId);

        Page<RoomListItemResponse> page = lazyListRepository.findRoomListItemsByUnitId(
                unitId, normalizeQuery(query), AccommodationStatus.AVAILABLE, AccommodationStatus.OCCUPIED, pageable);
        return PagedResponse.from(page);
    }

    @Transactional(readOnly = true)
    public PagedResponse<RoomListItemResponse> searchRoomsInSpace(
            UUID spaceId, UUID callerId, String query, Pageable pageable) {
        log.info("Searching rooms (lazy): spaceId={}, callerId={}", spaceId, callerId);

        accessService.assertCanViewStructure(spaceId, callerId);

        Page<RoomListItemResponse> page = lazyListRepository.findRoomListItemsBySpaceId(
                spaceId, normalizeQuery(query), AccommodationStatus.AVAILABLE, AccommodationStatus.OCCUPIED, pageable);
        return PagedResponse.from(page);
    }

    @Transactional(readOnly = true)
    public PagedResponse<BedListItemResponse> listBedsByRoom(
            UUID spaceId, UUID roomId, UUID callerId, String query, AccommodationStatus status, Pageable pageable) {
        log.info("Listing beds (lazy): spaceId={}, roomId={}, callerId={}", spaceId, roomId, callerId);

        accessService.assertCanViewStructure(spaceId, callerId);
        assertRoomInSpace(spaceId, roomId);

        Page<BedListItemResponse> page = lazyListRepository.findBedListItemsByRoomId(
                roomId, normalizeQuery(query), status, pageable);
        return PagedResponse.from(page);
    }

    @Transactional(readOnly = true)
    public PagedResponse<BedSpaceListItemResponse> searchBedsInSpace(
            UUID spaceId,
            UUID callerId,
            String query,
            AccommodationStatus status,
            UUID buildingId,
            UUID floorId,
            UUID unitId,
            Pageable pageable) {
        log.info(
                "Searching beds (lazy): spaceId={}, callerId={}, status={}, buildingId={}, floorId={}, unitId={}",
                spaceId,
                callerId,
                status,
                buildingId,
                floorId,
                unitId);

        accessService.assertCanViewStructure(spaceId, callerId);

        Page<BedSpaceListItemResponse> page = lazyListRepository.findBedListItemsBySpaceId(
                spaceId, normalizeQuery(query), status, buildingId, floorId, unitId, pageable);
        return PagedResponse.from(page);
    }

    private void assertBuildingInSpace(UUID spaceId, UUID buildingId) {
        buildingRepository
                .findActiveByIdAndSpaceId(buildingId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Building", "id", buildingId));
    }

    private void assertFloorInSpace(UUID spaceId, UUID floorId) {
        floorRepository
                .findActiveByIdAndSpaceId(floorId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Floor", "id", floorId));
    }

    private void assertFloorInBuilding(UUID buildingId, UUID floorId) {
        floorRepository
                .findActiveByIdAndBuildingId(floorId, buildingId)
                .orElseThrow(() -> new ResourceNotFoundException("Floor", "id", floorId));
    }

    private void assertUnitInSpace(UUID spaceId, UUID unitId) {
        unitRepository
                .findActiveByIdAndSpaceId(unitId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit", "id", unitId));
    }

    private void assertRoomInSpace(UUID spaceId, UUID roomId) {
        roomRepository
                .findActiveByIdAndSpaceId(roomId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Room", "id", roomId));
    }

    private String normalizeQuery(String query) {
        if (query == null) {
            return null;
        }
        String trimmed = query.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
