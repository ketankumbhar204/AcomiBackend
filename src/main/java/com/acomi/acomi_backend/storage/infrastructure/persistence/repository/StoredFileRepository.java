package com.acomi.acomi_backend.storage.infrastructure.persistence.repository;

import com.acomi.acomi_backend.storage.domain.model.FileStatus;
import com.acomi.acomi_backend.storage.infrastructure.persistence.entity.StoredFileEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoredFileRepository extends JpaRepository<StoredFileEntity, UUID> {

    List<StoredFileEntity> findByStatusAndExpiresAtBefore(FileStatus status, LocalDateTime cutoff);

    List<StoredFileEntity> findByStatusAndPurgeAfterBefore(FileStatus status, LocalDateTime cutoff);

    @Query(
            """
            SELECT f FROM StoredFileEntity f
            WHERE f.status = :status
              AND f.associated = false
              AND f.createdAt < :cutoff
            """)
    List<StoredFileEntity> findUnassociatedOlderThan(
            @Param("status") FileStatus status, @Param("cutoff") LocalDateTime cutoff);

    @Query(
            """
            SELECT COUNT(f) FROM StoredFileEntity f
            WHERE f.uploadedByUserId = :userId
              AND f.createdAt >= :since
            """)
    long countCreatedByUserSince(@Param("userId") UUID userId, @Param("since") LocalDateTime since);
}
