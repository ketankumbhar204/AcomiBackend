package com.countin.countin_backend.payment.infrastructure.persistence.repository;

import com.countin.countin_backend.payment.infrastructure.persistence.entity.SpacePaymentMonthSummaryEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpacePaymentMonthSummaryRepository
        extends JpaRepository<SpacePaymentMonthSummaryEntity, UUID> {

    Optional<SpacePaymentMonthSummaryEntity> findBySpaceIdAndMonth(UUID spaceId, String month);

    boolean existsBySpaceIdAndMonth(UUID spaceId, String month);
}
