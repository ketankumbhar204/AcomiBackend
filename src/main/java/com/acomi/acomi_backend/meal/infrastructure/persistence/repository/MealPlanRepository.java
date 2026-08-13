package com.acomi.acomi_backend.meal.infrastructure.persistence.repository;

import com.acomi.acomi_backend.meal.domain.model.MealPlanCode;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealPlanEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MealPlanRepository extends JpaRepository<MealPlanEntity, UUID> {

    List<MealPlanEntity> findBySpaceIdAndIsActiveTrueOrderBySortOrderAsc(UUID spaceId);

    Optional<MealPlanEntity> findByIdAndSpaceId(UUID id, UUID spaceId);

    Optional<MealPlanEntity> findBySpaceIdAndCode(UUID spaceId, MealPlanCode code);

    Optional<MealPlanEntity> findFirstBySpaceIdAndCodeOrderBySortOrderAsc(UUID spaceId, MealPlanCode code);

    boolean existsBySpaceIdAndCode(UUID spaceId, MealPlanCode code);

    boolean existsBySpaceId(UUID spaceId);
}
