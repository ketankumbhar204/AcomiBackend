package com.acomi.acomi_backend.member.infrastructure.persistence.repository;

import com.acomi.acomi_backend.member.domain.model.InvitationStatus;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.InvitationEntity;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InvitationRepository extends JpaRepository<InvitationEntity, UUID> {

    List<InvitationEntity> findByMobileNumber(String mobileNumber);

    List<InvitationEntity> findByStatus(InvitationStatus status);

    List<InvitationEntity> findBySpaceId(UUID spaceId);

    List<InvitationEntity> findBySpaceIdAndStatus(UUID spaceId, InvitationStatus status);

    Optional<InvitationEntity> findBySpaceIdAndMobileNumberAndStatus(
            UUID spaceId, String mobileNumber, InvitationStatus status);

    List<InvitationEntity> findByStatusAndExpiresAtBefore(InvitationStatus status, Instant now);

    boolean existsBySpaceIdAndMobileNumberAndStatus(
            UUID spaceId, String mobileNumber, InvitationStatus status);

    @Query("""
            SELECT i FROM InvitationEntity i
            JOIN FETCH i.space
            JOIN FETCH i.invitedBy
            WHERE i.mobileNumber = :mobileNumber
              AND i.status = com.acomi.acomi_backend.member.domain.model.InvitationStatus.PENDING
              AND i.expiresAt > :now
            ORDER BY i.createdAt DESC
            """)
    List<InvitationEntity> findActivePendingByMobileNumber(
            @Param("mobileNumber") String mobileNumber, @Param("now") LocalDateTime now);

    @Query("""
            SELECT i FROM InvitationEntity i
            JOIN FETCH i.invitedBy
            WHERE i.space.id = :spaceId
              AND i.status = com.acomi.acomi_backend.member.domain.model.InvitationStatus.PENDING
            ORDER BY i.createdAt DESC
            """)
    List<InvitationEntity> findPendingInvitations(@Param("spaceId") UUID spaceId);

    @Query("""
            SELECT i FROM InvitationEntity i
            JOIN FETCH i.space
            JOIN FETCH i.invitedBy
            WHERE i.id = :id
              AND i.status = com.acomi.acomi_backend.member.domain.model.InvitationStatus.PENDING
            """)
    Optional<InvitationEntity> findPendingInvitation(@Param("id") UUID id);

    List<InvitationEntity> findByInvitedBy_IdAndStatus(UUID invitedByUserId, InvitationStatus status);
}
