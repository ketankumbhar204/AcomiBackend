package com.countin.countin_backend.accommodation.application.service;

import com.countin.countin_backend.accommodation.api.dto.response.AvailabilityCountsResponse;
import com.countin.countin_backend.accommodation.api.dto.response.BuildingSummaryResponse;
import com.countin.countin_backend.accommodation.api.dto.response.StatusCountsResponse;
import com.countin.countin_backend.accommodation.api.dto.response.StructureCountsResponse;
import com.countin.countin_backend.accommodation.domain.model.AccommodationStatus;
import com.countin.countin_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import com.countin.countin_backend.accommodation.infrastructure.persistence.repository.AccommodationSummaryRepository;
import com.countin.countin_backend.accommodation.infrastructure.persistence.repository.BuildingRepository;
import com.countin.countin_backend.common.exception.ResourceNotFoundException;
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

        StatusCountsResponse statusCounts = aggregateStatusCounts(
                buildingId, counts.getBeds(), counts.getUnits());
        AvailabilityCountsResponse availability = buildAvailabilityCounts(buildingId);

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

    private AvailabilityCountsResponse buildAvailabilityCounts(UUID buildingId) {
        return AvailabilityCountsResponse.builder()
                .availableBeds(summaryRepository.countBedsByStatus(buildingId, AccommodationStatus.AVAILABLE))
                .occupiedBeds(summaryRepository.countBedsByStatus(buildingId, AccommodationStatus.OCCUPIED))
                .reservedBeds(summaryRepository.countBedsByStatus(buildingId, AccommodationStatus.RESERVED))
                .availableRooms(summaryRepository.countRoomsByStatus(buildingId, AccommodationStatus.AVAILABLE))
                .occupiedRooms(summaryRepository.countRoomsByStatus(buildingId, AccommodationStatus.OCCUPIED))
                .reservedRooms(summaryRepository.countRoomsByStatus(buildingId, AccommodationStatus.RESERVED))
                .availableUnits(summaryRepository.countUnitsByStatus(buildingId, AccommodationStatus.AVAILABLE))
                .occupiedUnits(summaryRepository.countUnitsByStatus(buildingId, AccommodationStatus.OCCUPIED))
                .reservedUnits(summaryRepository.countUnitsByStatus(buildingId, AccommodationStatus.RESERVED))
                .build();
    }

    /**
     * Bed-level status counts for PG/hostel layouts; unit-level for rental-only buildings.
     * Never merges room + bed + unit rows — that triple-counts a single occupied bed.
     */
    private StatusCountsResponse aggregateStatusCounts(UUID buildingId, long bedCount, long unitCount) {
        Map<AccommodationStatus, Long> totals = new EnumMap<>(AccommodationStatus.class);
        for (AccommodationStatus status : AccommodationStatus.values()) {
            totals.put(status, 0L);
        }

        if (bedCount > 0) {
            mergeStatusRows(totals, summaryRepository.countBedStatuses(buildingId));
        } else if (unitCount > 0) {
            mergeStatusRows(totals, summaryRepository.countUnitStatuses(buildingId));
        } else {
            mergeStatusRows(totals, summaryRepository.countRoomStatuses(buildingId));
        }

        return StatusCountsResponse.builder()
                .available(totals.get(AccommodationStatus.AVAILABLE))
                .occupied(totals.get(AccommodationStatus.OCCUPIED))
                .reserved(totals.get(AccommodationStatus.RESERVED))
                .maintenance(totals.get(AccommodationStatus.MAINTENANCE))
                .blocked(totals.get(AccommodationStatus.BLOCKED))
                .build();
    }

    private void mergeStatusRows(Map<AccommodationStatus, Long> totals, List<Object[]> rows) {
        for (Object[] row : rows) {
            AccommodationStatus status = (AccommodationStatus) row[0];
            long count = (Long) row[1];
            totals.merge(status, count, Long::sum);
        }
    }
}
