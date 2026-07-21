package com.countin.countin_backend.meal.infrastructure.persistence.repository;

import com.countin.countin_backend.meal.domain.model.MealType;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.MealParticipationDeliveryDefaultEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealParticipationDeliveryDefaultRepository
        extends JpaRepository<MealParticipationDeliveryDefaultEntity, UUID> {

    List<MealParticipationDeliveryDefaultEntity> findByParticipationId(UUID participationId);

    Optional<MealParticipationDeliveryDefaultEntity> findByParticipationIdAndMealType(
            UUID participationId, MealType mealType);

    void deleteByParticipationIdAndMealType(UUID participationId, MealType mealType);
}
