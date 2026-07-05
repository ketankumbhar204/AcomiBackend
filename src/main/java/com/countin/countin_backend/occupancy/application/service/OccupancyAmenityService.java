package com.countin.countin_backend.occupancy.application.service;

import com.countin.countin_backend.occupancy.infrastructure.persistence.entity.OccupancyAmenityEntity;
import com.countin.countin_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.countin.countin_backend.occupancy.infrastructure.persistence.repository.OccupancyAmenityRepository;
import com.countin.countin_backend.space.api.dto.AmenityAssignmentDto;
import com.countin.countin_backend.space.application.service.SpaceAmenityService;
import com.countin.countin_backend.space.domain.model.AmenityCode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OccupancyAmenityService {

    private final OccupancyAmenityRepository occupancyAmenityRepository;
    private final SpaceAmenityService spaceAmenityService;

    @Transactional(readOnly = true)
    public List<AmenityAssignmentDto> loadForOccupancy(UUID occupancyId) {
        return occupancyAmenityRepository
                .findAllByOccupancyIdOrderByDisplayOrderAscCreatedAtAsc(occupancyId)
                .stream()
                .map(OccupancyAmenityService::toDto)
                .toList();
    }

    @Transactional
    public void applyToOccupancy(
            OccupancyEntity occupancy,
            List<AmenityAssignmentDto> requested,
            List<AmenityAssignmentDto> fallback) {
        if (!SpaceAmenityService.supportsAmenities(occupancy.getSpace().getType())) {
            return;
        }

        UUID spaceId = occupancy.getSpace().getId();
        List<AmenityAssignmentDto> spaceAmenities = spaceAmenityService.getForSpace(spaceId);
        List<AmenityAssignmentDto> toAssign = resolveAssignments(requested, fallback, spaceAmenities);
        SpaceAmenityService.assertSubsetOfSpaceAmenities(toAssign, spaceAmenities);

        occupancyAmenityRepository.deleteByOccupancyId(occupancy.getId());
        int order = 0;
        for (AmenityAssignmentDto amenity : toAssign) {
            occupancyAmenityRepository.save(OccupancyAmenityEntity.builder()
                    .occupancy(occupancy)
                    .amenityCode(amenity.getCode())
                    .customLabel(AmenityCode.CUSTOM.name().equals(amenity.getCode()) ? amenity.getLabel() : null)
                    .displayOrder(order++)
                    .build());
        }
    }

    private List<AmenityAssignmentDto> resolveAssignments(
            List<AmenityAssignmentDto> requested,
            List<AmenityAssignmentDto> fallback,
            List<AmenityAssignmentDto> spaceAmenities) {
        if (requested != null) {
            return SpaceAmenityService.normalizeAssignments(requested);
        }
        if (fallback != null && !fallback.isEmpty()) {
            return new ArrayList<>(fallback);
        }
        return new ArrayList<>(spaceAmenities);
    }

    private static AmenityAssignmentDto toDto(OccupancyAmenityEntity entity) {
        AmenityCode code = AmenityCode.fromValue(entity.getAmenityCode())
                .orElse(AmenityCode.CUSTOM);
        AmenityAssignmentDto dto = new AmenityAssignmentDto();
        dto.setCode(entity.getAmenityCode());
        dto.setLabel(SpaceAmenityService.resolveLabel(
                code, entity.getCustomLabel() != null ? entity.getCustomLabel() : code.getDefaultLabel()));
        return dto;
    }
}
