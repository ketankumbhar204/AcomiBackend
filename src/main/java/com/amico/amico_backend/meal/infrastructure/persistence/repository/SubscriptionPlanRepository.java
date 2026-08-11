package com.amico.amico_backend.meal.infrastructure.persistence.repository;

import com.amico.amico_backend.meal.infrastructure.persistence.entity.SubscriptionPlanEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlanEntity, UUID> {

    List<SubscriptionPlanEntity> findBySpaceIdAndActiveTrueOrderBySortOrderAscNameAsc(UUID spaceId);

    List<SubscriptionPlanEntity> findBySpaceIdOrderBySortOrderAscNameAsc(UUID spaceId);

    Optional<SubscriptionPlanEntity> findByIdAndSpaceId(UUID id, UUID spaceId);
}
