package com.acomi.acomi_backend.admin.infrastructure.persistence.repository;

import com.acomi.acomi_backend.admin.infrastructure.persistence.entity.AdminNotificationTestAttemptEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminNotificationTestAttemptRepository
        extends JpaRepository<AdminNotificationTestAttemptEntity, UUID> {

    List<AdminNotificationTestAttemptEntity> findTop30ByAdminUserIdOrderByCreatedAtDesc(UUID adminUserId);

    Optional<AdminNotificationTestAttemptEntity> findByAdminUserIdAndClientRequestId(
            UUID adminUserId, String clientRequestId);

    @Modifying(clearAutomatically = true)
    @Query("delete from AdminNotificationTestAttemptEntity attempt where attempt.id = :id and attempt.adminUserId = :adminUserId")
    int deleteOwned(@Param("id") UUID id, @Param("adminUserId") UUID adminUserId);

    @Modifying(clearAutomatically = true)
    @Query("delete from AdminNotificationTestAttemptEntity attempt where attempt.adminUserId = :adminUserId")
    int deleteOwnedByAdmin(@Param("adminUserId") UUID adminUserId);
}
