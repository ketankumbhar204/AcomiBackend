package com.amico.amico_backend.meal.infrastructure.persistence.repository;

import com.amico.amico_backend.meal.domain.model.MealType;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealParticipationLastDeliveryEntity;
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
