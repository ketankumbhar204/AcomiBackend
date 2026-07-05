package com.countin.countin_backend.meal.domain.policy;

import com.countin.countin_backend.occupancy.domain.model.OccupancyStatus;
import com.countin.countin_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.countin.countin_backend.occupancy.infrastructure.persistence.repository.OccupancyRepository;
import com.countin.countin_backend.space.domain.model.SpaceType;
import com.countin.countin_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MealOccupancyPolicy {

    private final OccupancyRepository occupancyRepository;

    public boolean requiresActiveOccupancy(SpaceType spaceType) {
        return spaceType != SpaceType.MESS;
    }

    public boolean isOccupiedOnDate(OccupancyEntity occupancy, LocalDate date) {
        if (occupancy.getStatus() != OccupancyStatus.ACTIVE) {
            return false;
        }
        if (occupancy.getMoveInDate().isAfter(date)) {
            return false;
        }
        if (occupancy.getVacatedAt() != null && !occupancy.getVacatedAt().toLocalDate().isAfter(date)) {
            return false;
        }
        return true;
    }

    /**
     * Returns member IDs with active occupancy on the given date for accommodation spaces.
     * Empty optional means occupancy is not required (Mess).
     */
    public Optional<Set<UUID>> occupiedMemberIdsForDate(SpaceEntity space, LocalDate date) {
        if (!requiresActiveOccupancy(space.getType())) {
            return Optional.empty();
        }
        Set<UUID> memberIds = occupancyRepository.findActiveBySpaceId(space.getId()).stream()
                .filter(occupancy -> isOccupiedOnDate(occupancy, date))
                .map(occupancy -> occupancy.getMember().getId())
                .collect(Collectors.toSet());
        return Optional.of(memberIds);
    }

    public boolean hasOccupancyOnDate(SpaceEntity space, UUID memberId, LocalDate date) {
        Optional<Set<UUID>> occupiedMemberIds = occupiedMemberIdsForDate(space, date);
        return occupiedMemberIds.map(ids -> ids.contains(memberId)).orElse(true);
    }

    public boolean hasOccupancyOnDate(
            SpaceEntity space, UUID memberId, LocalDate date, Set<UUID> preloadedOccupiedMemberIds) {
        if (!requiresActiveOccupancy(space.getType())) {
            return true;
        }
        if (preloadedOccupiedMemberIds == null) {
            return hasOccupancyOnDate(space, memberId, date);
        }
        return preloadedOccupiedMemberIds.contains(memberId);
    }
}
