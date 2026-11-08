package com.acomi.acomi_backend.payment.infrastructure.persistence.repository;

import com.acomi.acomi_backend.payment.domain.model.MonthlyBillingNotificationStatus;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.MonthlyBillingNotificationEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MonthlyBillingNotificationRepository extends JpaRepository<MonthlyBillingNotificationEntity, UUID> {

    Optional<MonthlyBillingNotificationEntity> findByPaymentIdAndEventType(UUID paymentId, String eventType);

    long countByStatus(MonthlyBillingNotificationStatus status);

    List<MonthlyBillingNotificationEntity> findByStatus(
            MonthlyBillingNotificationStatus status, Pageable pageable);

    List<MonthlyBillingNotificationEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("""
            SELECT n FROM MonthlyBillingNotificationEntity n
            WHERE n.status = :status
              AND (n.nextAttemptAt IS NULL OR n.nextAttemptAt <= :now)
            ORDER BY n.createdAt ASC
            """)
    List<MonthlyBillingNotificationEntity> findDue(
            @Param("status") MonthlyBillingNotificationStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable);
}
