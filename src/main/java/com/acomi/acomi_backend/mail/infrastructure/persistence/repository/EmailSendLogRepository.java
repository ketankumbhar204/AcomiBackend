package com.acomi.acomi_backend.mail.infrastructure.persistence.repository;

import com.acomi.acomi_backend.mail.domain.model.EmailSendStatus;
import com.acomi.acomi_backend.mail.infrastructure.persistence.entity.EmailSendLogEntity;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailSendLogRepository extends JpaRepository<EmailSendLogEntity, UUID> {

    Optional<EmailSendLogEntity> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EmailSendLogEntity e WHERE e.id = :id")
    Optional<EmailSendLogEntity> lockById(@Param("id") UUID id);

    @Query(
            """
            SELECT e FROM EmailSendLogEntity e
            WHERE e.status IN :statuses
              AND e.attemptCount < :maxAttempts
              AND (e.lastAttemptAt IS NULL OR e.lastAttemptAt <= :retryAfter)
            ORDER BY e.createdAt ASC
            """)
    List<EmailSendLogEntity> findRetryCandidates(
            @Param("statuses") Collection<EmailSendStatus> statuses,
            @Param("maxAttempts") int maxAttempts,
            @Param("retryAfter") LocalDateTime retryAfter);
}
