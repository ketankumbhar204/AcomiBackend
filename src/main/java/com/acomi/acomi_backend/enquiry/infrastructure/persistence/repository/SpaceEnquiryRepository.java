package com.acomi.acomi_backend.enquiry.infrastructure.persistence.repository;

import com.acomi.acomi_backend.enquiry.domain.model.EnquiryRequesterType;
import com.acomi.acomi_backend.enquiry.domain.model.SpaceEnquiryStatus;
import com.acomi.acomi_backend.enquiry.infrastructure.persistence.entity.SpaceEnquiryEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SpaceEnquiryRepository
        extends JpaRepository<SpaceEnquiryEntity, UUID>, JpaSpecificationExecutor<SpaceEnquiryEntity> {

    Optional<SpaceEnquiryEntity> findBySpaceIdAndRequesterUserIdAndStatus(
            UUID spaceId, UUID requesterUserId, SpaceEnquiryStatus status);

    Optional<SpaceEnquiryEntity> findFirstBySpaceIdAndRequesterUserIdOrderByRequestedAtDesc(
            UUID spaceId, UUID requesterUserId);

    Page<SpaceEnquiryEntity> findByRequesterUserIdOrderByRequestedAtDesc(UUID requesterUserId, Pageable pageable);

    /**
     * Listing ids the requester has already enquired about.
     * A row counts only while it is still PENDING or SHARED and its expiry is still in the future.
     * EXPIRED, REJECTED, CANCELLED, and any PENDING/SHARED row past expiresAt are omitted
     * so the user can enquire again.
     */
    @Query("""
            SELECT e
            FROM SpaceEnquiryEntity e
            WHERE e.requesterUserId = :requesterId
              AND e.status IN (
                    com.acomi.acomi_backend.enquiry.domain.model.SpaceEnquiryStatus.PENDING,
                    com.acomi.acomi_backend.enquiry.domain.model.SpaceEnquiryStatus.SHARED
                  )
              AND e.expiresAt > :now
            """)
    List<SpaceEnquiryEntity> findActiveByRequester(
            @Param("requesterId") UUID requesterId, @Param("now") LocalDateTime now);

    Page<SpaceEnquiryEntity> findAllByOrderByRequestedAtDesc(Pageable pageable);

    Page<SpaceEnquiryEntity> findByStatusOrderByRequestedAtDesc(SpaceEnquiryStatus status, Pageable pageable);

    Optional<SpaceEnquiryEntity> findByIdAndRequesterUserId(UUID id, UUID requesterUserId);

    long countByStatus(SpaceEnquiryStatus status);

    @Modifying
    @Query("""
            UPDATE SpaceEnquiryEntity e
            SET e.status = com.acomi.acomi_backend.enquiry.domain.model.SpaceEnquiryStatus.EXPIRED,
                e.updatedAt = :now
            WHERE e.status = com.acomi.acomi_backend.enquiry.domain.model.SpaceEnquiryStatus.PENDING
              AND e.expiresAt <= :now
            """)
    int expirePendingDue(@Param("now") LocalDateTime now);

    List<SpaceEnquiryEntity> findByStatusAndExpiresAtLessThanEqual(
            SpaceEnquiryStatus status, LocalDateTime expiresAt);

    long countByRequestedAtGreaterThanEqualAndRequestedAtLessThan(LocalDateTime from, LocalDateTime to);

    long countByRequesterUserIdAndRequestedAtGreaterThanEqual(UUID requesterUserId, LocalDateTime from);

    @Query(
            value =
                    """
                    SELECT CAST(e.requested_at AS date) AS day, COUNT(*) AS cnt
                    FROM space_enquiries e
                    WHERE e.requested_at >= :fromAt AND e.requested_at < :toAt
                    GROUP BY CAST(e.requested_at AS date)
                    ORDER BY day ASC
                    """,
            nativeQuery = true)
    List<Object[]> countDailyRequestedBetween(
            @Param("fromAt") LocalDateTime fromAt, @Param("toAt") LocalDateTime toAt);

    @Query("""
            SELECT e FROM SpaceEnquiryEntity e
            WHERE e.spaceId IN :spaceIds
              AND (CAST(:status AS string) IS NULL OR e.status = :status)
              AND (CAST(:fromAt AS timestamp) IS NULL OR e.requestedAt >= :fromAt)
              AND (CAST(:toAt AS timestamp) IS NULL OR e.requestedAt < :toAt)
              AND (
                   CAST(:q AS string) IS NULL OR CAST(:q AS string) = '' OR
                   LOWER(e.spaceNameSnapshot) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) OR
                   LOWER(e.requesterNameSnapshot) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) OR
                   LOWER(COALESCE(e.requesterEmail, '')) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) OR
                   UPPER(e.referenceCode) = UPPER(TRIM(CAST(:q AS string)))
              )
            ORDER BY e.requestedAt DESC
            """)
    Page<SpaceEnquiryEntity> searchReceived(
            @Param("spaceIds") java.util.Collection<UUID> spaceIds,
            @Param("status") SpaceEnquiryStatus status,
            @Param("fromAt") LocalDateTime fromAt,
            @Param("toAt") LocalDateTime toAt,
            @Param("q") String query,
            Pageable pageable);

    @Query("""
            SELECT e.status, COUNT(e)
            FROM SpaceEnquiryEntity e
            WHERE e.spaceId IN :spaceIds
            GROUP BY e.status
            """)
    List<Object[]> countReceivedByStatus(@Param("spaceIds") java.util.Collection<UUID> spaceIds);

    @Query(
            """
            SELECT e FROM SpaceEnquiryEntity e
            WHERE (CAST(:status AS string) IS NULL OR e.status = :status)
              AND (CAST(:requesterType AS string) IS NULL OR e.requesterType = :requesterType)
              AND (CAST(:fromAt AS timestamp) IS NULL OR e.requestedAt >= :fromAt)
              AND (CAST(:toAt AS timestamp) IS NULL OR e.requestedAt < :toAt)
              AND (
                   CAST(:q AS string) IS NULL OR CAST(:q AS string) = '' OR
                   LOWER(e.spaceNameSnapshot) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) OR
                   LOWER(e.requesterNameSnapshot) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) OR
                   LOWER(COALESCE(e.requesterEmail, '')) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) OR
                   UPPER(e.referenceCode) = UPPER(TRIM(CAST(:q AS string)))
              )
            ORDER BY e.requestedAt DESC
            """)
    Page<SpaceEnquiryEntity> searchForAdmin(
            @Param("status") SpaceEnquiryStatus status,
            @Param("requesterType") EnquiryRequesterType requesterType,
            @Param("q") String q,
            @Param("fromAt") LocalDateTime fromAt,
            @Param("toAt") LocalDateTime toAt,
            Pageable pageable);
}
