package com.amico.amico_backend.accommodation.application.service;

import com.amico.amico_backend.accommodation.api.dto.response.AvailabilityCountsResponse;
import com.amico.amico_backend.accommodation.api.dto.response.BuildingSummaryResponse;
import com.amico.amico_backend.accommodation.api.dto.response.StatusCountsResponse;
import com.amico.amico_backend.accommodation.api.dto.response.StructureCountsResponse;
import com.amico.amico_backend.accommodation.domain.model.AccommodationStatus;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.repository.AccommodationSummaryRepository;
import com.amico.amico_backend.accommodation.infrastructure.persistence.repository.BuildingRepository;
import com.amico.amico_backend.common.exception.ResourceNotFoundException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccommodationSummaryService {

    private final AccommodationAccessService accessService;
    private final BuildingRepository buildingRepository;
    private final AccommodationSummaryRepository summaryRepository;

    @Transactional(readOnly = true)
    public BuildingSummaryResponse getBuildingSummary(UUID spaceId, UUID buildingId, UUID callerId) {
        log.info("Fetching building summary: spaceId={}, buildingId={}, callerId={}",
                spaceId, buildingId, callerId);

        accessService.assertCanViewStructure(spaceId, callerId);

        BuildingEntity building = buildingRepository.findActiveByIdAndSpaceId(buildingId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Building", "id", buildingId));

        long unitCount = summaryRepository.countActiveUnits(buildingId);
        long visibleUnitCount = summaryRepository.countVisibleActiveUnits(buildingId);
        long syntheticUnitCount = summaryRepository.countSyntheticActiveUnits(buildingId);

        StructureCountsResponse counts = StructureCountsResponse.builder()
                .floors(summaryRepository.countActiveFloors(buildingId))
                .units(unitCount)
                .rooms(summaryRepository.countActiveRooms(buildingId))
                .beds(summaryRepository.countActiveBeds(buildingId))
                .build();

        // One GROUP BY per entity type — never 9× count-by-status round trips.
        Map<AccommodationStatus, Long> bedByStatus = statusMap(summaryRepository.countBedStatuses(buildingId));
        Map<AccommodationStatus, Long> roomByStatus = statusMap(summaryRepository.countRoomStatuses(buildingId));
        Map<AccommodationStatus, Long> unitByStatus = statusMap(summaryRepository.countUnitStatuses(buildingId));

        StatusCountsResponse statusCounts = aggregateStatusCounts(
                counts.getBeds(), counts.getUnits(), bedByStatus, roomByStatus, unitByStatus);
        AvailabilityCountsResponse availability =
                buildAvailabilityCounts(bedByStatus, roomByStatus, unitByStatus);

        return BuildingSummaryResponse.builder()
                .buildingId(building.getId())
                .name(building.getName())
                .code(building.getCode())
                .spaceId(spaceId)
                .layoutMode(building.getLayoutMode())
                .unitCount(unitCount)
                .visibleUnitCount(visibleUnitCount)
                .syntheticUnitCount(syntheticUnitCount)
                .counts(counts)
                .statusCounts(statusCounts)
                .availability(availability)
                .build();
    }

    private static AvailabilityCountsResponse buildAvailabilityCounts(
            Map<AccommodationStatus, Long> bedByStatus,
            Map<AccommodationStatus, Long> roomByStatus,
            Map<AccommodationStatus, Long> unitByStatus) {
        return AvailabilityCountsResponse.builder()
                .availableBeds(bedByStatus.getOrDefault(AccommodationStatus.AVAILABLE, 0L))
                .occupiedBeds(bedByStatus.getOrDefault(AccommodationStatus.OCCUPIED, 0L))
                .reservedBeds(bedByStatus.getOrDefault(AccommodationStatus.RESERVED, 0L))
                .availableRooms(roomByStatus.getOrDefault(AccommodationStatus.AVAILABLE, 0L))
                .occupiedRooms(roomByStatus.getOrDefault(AccommodationStatus.OCCUPIED, 0L))
                .reservedRooms(roomByStatus.getOrDefault(AccommodationStatus.RESERVED, 0L))
                .availableUnits(unitByStatus.getOrDefault(AccommodationStatus.AVAILABLE, 0L))
                .occupiedUnits(unitByStatus.getOrDefault(AccommodationStatus.OCCUPIED, 0L))
                .reservedUnits(unitByStatus.getOrDefault(AccommodationStatus.RESERVED, 0L))
                .build();
    }

    private static Map<AccommodationStatus, Long> statusMap(List<Object[]> rows) {
        Map<AccommodationStatus, Long> totals = new EnumMap<>(AccommodationStatus.class);
        for (Object[] row : rows) {
            totals.put((AccommodationStatus) row[0], (Long) row[1]);
        }
        return totals;
    }

    /**
     * Bed-level status counts for PG/hostel layouts; unit-level for rental-only buildings.
     * Never merges room + bed + unit rows — that triple-counts a single occupied bed.
     */
    private static StatusCountsResponse aggregateStatusCounts(
            long bedCount,
            long unitCount,
            Map<AccommodationStatus, Long> bedByStatus,
            Map<AccommodationStatus, Long> roomByStatus,
            Map<AccommodationStatus, Long> unitByStatus) {
        Map<AccommodationStatus, Long> totals;
        if (bedCount > 0) {
            totals = bedByStatus;
        } else if (unitCount > 0) {
            totals = unitByStatus;
        } else {
            totals = roomByStatus;
        }

        return StatusCountsResponse.builder()
                .available(totals.getOrDefault(AccommodationStatus.AVAILABLE, 0L))
                .occupied(totals.getOrDefault(AccommodationStatus.OCCUPIED, 0L))
                .reserved(totals.getOrDefault(AccommodationStatus.RESERVED, 0L))
                .maintenance(totals.getOrDefault(AccommodationStatus.MAINTENANCE, 0L))
                .blocked(totals.getOrDefault(AccommodationStatus.BLOCKED, 0L))
                .build();
    }
}
