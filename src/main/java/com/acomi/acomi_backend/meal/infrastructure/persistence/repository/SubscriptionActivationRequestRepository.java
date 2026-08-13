package com.acomi.acomi_backend.meal.infrastructure.persistence.repository;

import com.acomi.acomi_backend.meal.domain.model.SubscriptionActivationRequestStatus;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.SubscriptionActivationRequestEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionActivationRequestRepository
        extends JpaRepository<SubscriptionActivationRequestEntity, UUID> {

    List<SubscriptionActivationRequestEntity> findBySpaceIdAndStatusOrderByCreatedAtDesc(
            UUID spaceId, SubscriptionActivationRequestStatus status);

    List<SubscriptionActivationRequestEntity> findByMemberIdOrderByCreatedAtDesc(UUID memberId);

    Optional<SubscriptionActivationRequestEntity> findFirstByMemberIdAndStatusOrderByCreatedAtDesc(
            UUID memberId, SubscriptionActivationRequestStatus status);

    Optional<SubscriptionActivationRequestEntity> findByIdAndSpaceId(UUID id, UUID spaceId);
}
