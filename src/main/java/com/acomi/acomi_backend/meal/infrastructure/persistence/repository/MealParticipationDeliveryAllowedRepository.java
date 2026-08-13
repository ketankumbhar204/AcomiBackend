package com.acomi.acomi_backend.meal.infrastructure.persistence.repository;

import com.acomi.acomi_backend.meal.domain.model.MealType;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealParticipationDeliveryAllowedEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealParticipationDeliveryAllowedRepository
        extends JpaRepository<MealParticipationDeliveryAllowedEntity, UUID> {

    List<MealParticipationDeliveryAllowedEntity> findByParticipationId(UUID participationId);

    List<MealParticipationDeliveryAllowedEntity> findByParticipationIdAndMealType(
            UUID participationId, MealType mealType);

    void deleteByParticipationIdAndMealType(UUID participationId, MealType mealType);
}
