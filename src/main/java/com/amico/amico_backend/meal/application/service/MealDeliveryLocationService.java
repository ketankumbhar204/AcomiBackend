package com.amico.amico_backend.meal.application.service;

import com.amico.amico_backend.common.exception.BusinessException;
import com.amico.amico_backend.meal.api.dto.request.CreateMealDeliveryLocationRequest;
import com.amico.amico_backend.meal.api.dto.request.ReorderMealDeliveryLocationsRequest;
import com.amico.amico_backend.meal.api.dto.request.UpdateMealDeliveryLocationRequest;
import com.amico.amico_backend.meal.api.dto.response.MealDeliveryLocationResponse;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealDeliveryLocationEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.repository.MealDeliveryLocationRepository;
import com.amico.amico_backend.space.domain.model.SpaceType;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.amico.amico_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MealDeliveryLocationService {

    private final MealDeliveryLocationRepository locationRepository;
    private final SpaceRepository spaceRepository;
    private final MealAccessService mealAccessService;

    @Transactional(readOnly = true)
    public List<MealDeliveryLocationResponse> listActive(UUID spaceId, UUID callerId) {
        mealAccessService.requireViewMeals(spaceId, callerId);
        requireMessSpace(spaceId);
        return locationRepository.findBySpaceIdAndActiveTrueOrderBySortOrderAscNameAsc(spaceId).stream()
                .map(MealDeliveryLocationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MealDeliveryLocationResponse> listAll(UUID spaceId, UUID callerId) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        requireMessSpace(spaceId);
        return locationRepository.findBySpaceIdOrderBySortOrderAscNameAsc(spaceId).stream()
                .map(MealDeliveryLocationResponse::from)
                .toList();
    }

    @Transactional
    public MealDeliveryLocationResponse create(
            UUID spaceId, UUID callerId, CreateMealDeliveryLocationRequest request) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        SpaceEntity space = requireMessSpace(spaceId);

        MealDeliveryLocationEntity entity = MealDeliveryLocationEntity.builder()
                .space(space)
                .name(request.getName().trim())
                .description(trimToNull(request.getDescription()))
                .address(trimToNull(request.getAddress()))
                .active(true)
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : nextSortOrder(spaceId))
                .build();
        return MealDeliveryLocationResponse.from(locationRepository.save(entity));
    }

    @Transactional
    public MealDeliveryLocationResponse update(
            UUID spaceId, UUID locationId, UUID callerId, UpdateMealDeliveryLocationRequest request) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        requireMessSpace(spaceId);

        MealDeliveryLocationEntity entity = loadLocation(spaceId, locationId);
        if (request.getName() != null && !request.getName().isBlank()) {
            entity.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            entity.setDescription(trimToNull(request.getDescription()));
        }
        if (request.getAddress() != null) {
            entity.setAddress(trimToNull(request.getAddress()));
        }
        if (request.getActive() != null) {
            entity.setActive(request.getActive());
        }
        if (request.getSortOrder() != null) {
            entity.setSortOrder(request.getSortOrder());
        }
        return MealDeliveryLocationResponse.from(locationRepository.save(entity));
    }

    @Transactional
    public List<MealDeliveryLocationResponse> reorder(
            UUID spaceId, UUID callerId, ReorderMealDeliveryLocationsRequest request) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        requireMessSpace(spaceId);

        List<MealDeliveryLocationEntity> existing =
                locationRepository.findBySpaceIdOrderBySortOrderAscNameAsc(spaceId);
        if (existing.size() != request.getLocationIdsInOrder().size()) {
            throw new BusinessException("Reorder list must include all delivery locations", HttpStatus.BAD_REQUEST);
        }

        var existingIds = existing.stream()
                .map(MealDeliveryLocationEntity::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (!existingIds.equals(new java.util.HashSet<>(request.getLocationIdsInOrder()))) {
            throw new BusinessException("Reorder list contains invalid delivery locations", HttpStatus.BAD_REQUEST);
        }

        var byId = existing.stream()
                .collect(java.util.stream.Collectors.toMap(MealDeliveryLocationEntity::getId, row -> row));
        int order = 0;
        for (UUID locationId : request.getLocationIdsInOrder()) {
            byId.get(locationId).setSortOrder(order++);
        }
        locationRepository.saveAll(existing);

        return locationRepository.findBySpaceIdOrderBySortOrderAscNameAsc(spaceId).stream()
                .map(MealDeliveryLocationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MealDeliveryLocationEntity loadActiveLocation(UUID spaceId, UUID locationId) {
        MealDeliveryLocationEntity location = locationRepository
                .findByIdAndSpaceId(locationId, spaceId)
                .orElseThrow(() -> new BusinessException("Delivery location not found", HttpStatus.BAD_REQUEST));
        if (!location.isActive()) {
            throw new BusinessException("Delivery location is not active", HttpStatus.BAD_REQUEST);
        }
        return location;
    }

    @Transactional(readOnly = true)
    public boolean hasActiveLocations(UUID spaceId) {
        return !locationRepository.findBySpaceIdAndActiveTrueOrderBySortOrderAscNameAsc(spaceId).isEmpty();
    }

    private MealDeliveryLocationEntity loadLocation(UUID spaceId, UUID locationId) {
        return locationRepository
                .findByIdAndSpaceId(locationId, spaceId)
                .orElseThrow(() -> new BusinessException("Delivery location not found", HttpStatus.NOT_FOUND));
    }

    private SpaceEntity requireMessSpace(UUID spaceId) {
        SpaceEntity space = spaceRepository
                .findById(spaceId)
                .orElseThrow(() -> new BusinessException("Space not found", HttpStatus.NOT_FOUND));
        if (space.getType() != SpaceType.MESS) {
            throw new BusinessException("Delivery locations are only available for mess spaces", HttpStatus.BAD_REQUEST);
        }
        return space;
    }

    private int nextSortOrder(UUID spaceId) {
        return locationRepository.findMaxSortOrderBySpaceId(spaceId) + 1;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
