package com.acomi.acomi_backend.payment.infrastructure.persistence.repository;

import com.acomi.acomi_backend.payment.domain.model.MonthlyBillingRunStatus;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.MonthlyBillingRunEntity;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyBillingRunRepository extends JpaRepository<MonthlyBillingRunEntity, UUID> {

    Optional<MonthlyBillingRunEntity> findBySpaceIdAndBillingMonth(UUID spaceId, LocalDate billingMonth);

    List<MonthlyBillingRunEntity> findBySpaceIdAndStatusIn(UUID spaceId, Collection<MonthlyBillingRunStatus> statuses);

    List<MonthlyBillingRunEntity> findAllByOrderByStartedAtDesc(Pageable pageable);

    Optional<MonthlyBillingRunEntity> findFirstByStatusOrderByFinishedAtDesc(MonthlyBillingRunStatus status);
}
