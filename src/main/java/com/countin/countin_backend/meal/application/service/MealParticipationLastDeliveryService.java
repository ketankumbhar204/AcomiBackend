package com.countin.countin_backend.meal.application.service;

import com.countin.countin_backend.meal.domain.model.MealType;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.MealDeliveryLocationEntity;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.MealParticipationEntity;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.MealParticipationLastDeliveryEntity;
import com.countin.countin_backend.meal.infrastructure.persistence.repository.MealDeliveryLocationRepository;
import com.countin.countin_backend.meal.infrastructure.persistence.repository.MealParticipationLastDeliveryRepository;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MealParticipationLastDeliveryService {

    private final MealParticipationLastDeliveryRepository lastDeliveryRepository;
    private final MealDeliveryLocationService deliveryLocationService;
    private final MealDeliveryLocationRepository deliveryLocationRepository;

    @Transactional(readOnly = true)
    public Map<MealType, UUID> loadLastDeliveryLocationIds(MealParticipationEntity participation) {
        List<MealParticipationLastDeliveryEntity> rows =
                lastDeliveryRepository.findByParticipationId(participation.getId());
        Map<MealType, UUID> result = new EnumMap<>(MealType.class);
        for (MealParticipationLastDeliveryEntity row : rows) {
            MealDeliveryLocationEntity location = row.getDeliveryLocation();
            if (location != null && location.isActive()) {
                result.put(row.getMealType(), location.getId());
            }
        }
        return result;
    }

    /**
     * MVP preference for poll UI: last selected if still active, otherwise first active catalog
     * location. Always returns a value for every meal type when the space has active locations.
     */
    @Transactional(readOnly = true)
    public Map<MealType, UUID> resolvePreferredDeliveryLocationIds(
            MealParticipationEntity participation, List<MealDeliveryLocationEntity> activeLocations) {
        Map<MealType, UUID> preferred = new EnumMap<>(MealType.class);
        if (activeLocations == null || activeLocations.isEmpty()) {
            return preferred;
        }

        UUID firstActiveId = activeLocations.get(0).getId();
        Map<MealType, UUID> lastActive = loadLastDeliveryLocationIds(participation);
        for (MealType mealType : MealType.values()) {
            preferred.put(mealType, lastActive.getOrDefault(mealType, firstActiveId));
        }
        return preferred;
    }

    @Transactional(readOnly = true)
    public UUID resolveLastDeliveryLocationId(MealParticipationEntity participation, MealType mealType) {
        return lastDeliveryRepository
                .findByParticipationIdAndMealType(participation.getId(), mealType)
                .map(MealParticipationLastDeliveryEntity::getDeliveryLocation)
                .filter(location -> location != null && location.isActive())
                .map(MealDeliveryLocationEntity::getId)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public UUID resolvePreferredDeliveryLocationId(
            MealParticipationEntity participation, MealType mealType) {
        UUID last = resolveLastDeliveryLocationId(participation, mealType);
        if (last != null) {
            return last;
        }
        return deliveryLocationRepository
                .findBySpaceIdAndActiveTrueOrderBySortOrderAscNameAsc(participation.getSpace().getId())
                .stream()
                .findFirst()
                .map(MealDeliveryLocationEntity::getId)
                .orElse(null);
    }

    @Transactional
    public void saveLastDeliveryLocation(
            MealParticipationEntity participation, MealType mealType, UUID deliveryLocationId) {
        MealDeliveryLocationEntity location =
                deliveryLocationService.loadActiveLocation(participation.getSpace().getId(), deliveryLocationId);

        MealParticipationLastDeliveryEntity row = lastDeliveryRepository
                .findByParticipationIdAndMealType(participation.getId(), mealType)
                .orElse(MealParticipationLastDeliveryEntity.builder()
                        .participation(participation)
                        .mealType(mealType)
                        .build());
        row.setDeliveryLocation(location);
        lastDeliveryRepository.save(row);
    }
}
