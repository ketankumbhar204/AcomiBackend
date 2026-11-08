package com.acomi.acomi_backend.occupancy.application.service;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.occupancy.application.support.StayAvailabilityRules;
import com.acomi.acomi_backend.occupancy.domain.model.AllocationTargetType;
import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.repository.OccupancyRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Date overlap for a proposed stay. The status unique indexes still forbid a second
 * open row on the same target. This check adds vacated history and room/bed ranges.
 */
@Service
@RequiredArgsConstructor
public class StayRangeGuard {

    static final Collection<OccupancyStatus> BLOCKING_STATUSES =
            List.of(OccupancyStatus.ACTIVE, OccupancyStatus.RESERVED, OccupancyStatus.VACATED);

    private final OccupancyRepository occupancyRepository;

    public void assertAvailable(
            AllocationTargetType targetType,
            UUID bedId,
            UUID roomId,
            UUID unitId,
            LocalDate start,
            LocalDate exclusiveEnd,
            UUID excludeOccupancyId) {
        LinkedHashMap<UUID, OccupancyEntity> candidates = new LinkedHashMap<>();
        if (targetType == AllocationTargetType.BED && bedId != null) {
            addAll(candidates, occupancyRepository.findByBed_IdAndStatusIn(bedId, BLOCKING_STATUSES));
            if (roomId != null) {
                addAll(
                        candidates,
                        occupancyRepository.findByRoom_IdAndTargetTypeAndStatusIn(
                                roomId, AllocationTargetType.ROOM, BLOCKING_STATUSES));
            }
        } else if (targetType == AllocationTargetType.ROOM && roomId != null) {
            addAll(
                    candidates,
                    occupancyRepository.findByRoom_IdAndTargetTypeAndStatusIn(
                            roomId, AllocationTargetType.ROOM, BLOCKING_STATUSES));
            addAll(
                    candidates,
                    occupancyRepository.findByRoom_IdAndTargetTypeAndStatusIn(
                            roomId, AllocationTargetType.BED, BLOCKING_STATUSES));
        } else if (targetType == AllocationTargetType.UNIT && unitId != null) {
            addAll(candidates, occupancyRepository.findByUnit_IdAndStatusIn(unitId, BLOCKING_STATUSES));
        }

        for (OccupancyEntity other : candidates.values()) {
            if (excludeOccupancyId != null && excludeOccupancyId.equals(other.getId())) {
                continue;
            }
            if (StayAvailabilityRules.overlaps(
                    start,
                    exclusiveEnd,
                    StayAvailabilityRules.blockingStart(other),
                    StayAvailabilityRules.blockingEnd(other))) {
                throw new BusinessException("STAY_OVERLAP", StayAvailabilityRules.OVERLAP);
            }
        }
    }

    private static void addAll(LinkedHashMap<UUID, OccupancyEntity> candidates, List<OccupancyEntity> rows) {
        for (OccupancyEntity row : rows) {
            if (row.getId() != null) {
                candidates.put(row.getId(), row);
            }
        }
    }
}
