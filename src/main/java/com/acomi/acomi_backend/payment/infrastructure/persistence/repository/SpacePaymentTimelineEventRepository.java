package com.acomi.acomi_backend.payment.infrastructure.persistence.repository;

import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.SpacePaymentTimelineEventEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpacePaymentTimelineEventRepository
        extends JpaRepository<SpacePaymentTimelineEventEntity, UUID> {

    List<SpacePaymentTimelineEventEntity> findByPaymentIdOrderByPerformedAtAsc(UUID paymentId);
}
