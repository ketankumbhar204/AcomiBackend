package com.acomi.acomi_backend.meal.infrastructure.persistence.repository;

import com.acomi.acomi_backend.meal.domain.model.MealBillingChangeRequestStatus;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealBillingChangeRequestEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealBillingChangeRequestRepository extends JpaRepository<MealBillingChangeRequestEntity, UUID> {

    List<MealBillingChangeRequestEntity> findBySpaceIdAndStatusOrderByCreatedAtDesc(
            UUID spaceId, MealBillingChangeRequestStatus status);

    Optional<MealBillingChangeRequestEntity> findFirstByMemberIdAndStatusOrderByCreatedAtDesc(
            UUID memberId, MealBillingChangeRequestStatus status);

    Optional<MealBillingChangeRequestEntity> findByIdAndSpaceId(UUID id, UUID spaceId);
}
