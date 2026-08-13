package com.acomi.acomi_backend.meal.infrastructure.persistence.repository;

import com.acomi.acomi_backend.meal.domain.model.MealType;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealParticipationLastDeliveryEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealParticipationLastDeliveryRepository
        extends JpaRepository<MealParticipationLastDeliveryEntity, UUID> {

    List<MealParticipationLastDeliveryEntity> findByParticipationId(UUID participationId);

    Optional<MealParticipationLastDeliveryEntity> findByParticipationIdAndMealType(
            UUID participationId, MealType mealType);
}
