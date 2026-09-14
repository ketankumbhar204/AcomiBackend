package com.acomi.acomi_backend.payment.infrastructure.persistence.repository;

import com.acomi.acomi_backend.payment.domain.model.SpacePaymentCategory;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentType;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SpacePaymentRepository extends JpaRepository<SpacePaymentEntity, UUID> {

    @Query("""
            SELECT p FROM SpacePaymentEntity p
            JOIN FETCH p.member m
            LEFT JOIN FETCH m.user
            LEFT JOIN FETCH p.occupancy o
            LEFT JOIN FETCH o.building
            LEFT JOIN FETCH o.floor
            LEFT JOIN FETCH o.unit
            LEFT JOIN FETCH o.room
            LEFT JOIN FETCH o.bed
            WHERE p.id = :paymentId AND p.space.id = :spaceId
            """)
    Optional<SpacePaymentEntity> findByIdAndSpaceId(
            @Param("paymentId") UUID paymentId, @Param("spaceId") UUID spaceId);

    /** Slim list query — avoid redundant o.space / u.floor joins that inflate result graphs. */
    @Query("""
            SELECT p FROM SpacePaymentEntity p
            JOIN FETCH p.member
            LEFT JOIN FETCH p.occupancy o
            LEFT JOIN FETCH o.building
            LEFT JOIN FETCH o.floor
            LEFT JOIN FETCH o.unit
            LEFT JOIN FETCH o.room
            LEFT JOIN FETCH o.bed
            WHERE p.space.id = :spaceId
              AND p.month = :month
              AND (:memberId IS NULL OR p.member.id = :memberId)
              AND (:status IS NULL OR p.paymentStatus = :status)
              AND (:paymentType IS NULL OR p.paymentType = :paymentType)
              AND (:paymentCategory IS NULL OR p.paymentCategory = :paymentCategory)
            ORDER BY p.dueDate ASC, p.member.fullName ASC
            """)
    List<SpacePaymentEntity> search(
            @Param("spaceId") UUID spaceId,
            @Param("month") String month,
            @Param("memberId") UUID memberId,
            @Param("status") SpacePaymentStatus status,
            @Param("paymentType") SpacePaymentType paymentType,
            @Param("paymentCategory") SpacePaymentCategory paymentCategory);

    @Query(
            value = """
                    SELECT p FROM SpacePaymentEntity p
                    JOIN p.member m
                    WHERE p.space.id = :spaceId
                      AND p.month = :month
                      AND (:memberId IS NULL OR m.id = :memberId)
                      AND (:statusesEmpty = true OR p.paymentStatus IN :statuses)
                      AND (:paymentType IS NULL OR p.paymentType = :paymentType)
                      AND (:excludePaymentType IS NULL OR p.paymentType <> :excludePaymentType)
                      AND (:paymentCategory IS NULL OR p.paymentCategory = :paymentCategory)
                    """,
            countQuery = """
                    SELECT COUNT(p) FROM SpacePaymentEntity p
                    JOIN p.member m
                    WHERE p.space.id = :spaceId
                      AND p.month = :month
                      AND (:memberId IS NULL OR m.id = :memberId)
                      AND (:statusesEmpty = true OR p.paymentStatus IN :statuses)
                      AND (:paymentType IS NULL OR p.paymentType = :paymentType)
                      AND (:excludePaymentType IS NULL OR p.paymentType <> :excludePaymentType)
                      AND (:paymentCategory IS NULL OR p.paymentCategory = :paymentCategory)
                    """)
    Page<SpacePaymentEntity> searchPaged(
            @Param("spaceId") UUID spaceId,
            @Param("month") String month,
            @Param("memberId") UUID memberId,
            @Param("statuses") Collection<SpacePaymentStatus> statuses,
            @Param("statusesEmpty") boolean statusesEmpty,
            @Param("paymentType") SpacePaymentType paymentType,
            @Param("excludePaymentType") SpacePaymentType excludePaymentType,
            @Param("paymentCategory") SpacePaymentCategory paymentCategory,
            Pageable pageable);

    @Query("""
            SELECT p FROM SpacePaymentEntity p
            JOIN FETCH p.member
            LEFT JOIN FETCH p.occupancy o
            LEFT JOIN FETCH o.building
            LEFT JOIN FETCH o.floor
            LEFT JOIN FETCH o.unit
            LEFT JOIN FETCH o.room
            LEFT JOIN FETCH o.bed
            WHERE p.id IN :ids
            """)
    List<SpacePaymentEntity> findAllByIdInWithGraph(@Param("ids") Collection<UUID> ids);

    @Query("""
            SELECT p.paymentStatus, COUNT(p)
            FROM SpacePaymentEntity p
            WHERE p.space.id = :spaceId AND p.month = :month
            GROUP BY p.paymentStatus
            """)
    List<Object[]> countGroupedByStatus(@Param("spaceId") UUID spaceId, @Param("month") String month);

    Optional<SpacePaymentEntity> findBySpaceIdAndMemberIdAndMonthAndPaymentTypeAndPaymentCategory(
            UUID spaceId,
            UUID memberId,
            String month,
            SpacePaymentType paymentType,
            SpacePaymentCategory paymentCategory);

    Optional<SpacePaymentEntity>
            findBySpaceIdAndMemberIdAndMonthAndPaymentTypeAndPaymentCategoryAndDueDate(
                    UUID spaceId,
                    UUID memberId,
                    String month,
                    SpacePaymentType paymentType,
                    SpacePaymentCategory paymentCategory,
                    java.time.LocalDate dueDate);

    Optional<SpacePaymentEntity> findBySpaceIdAndPaymentBatchId(UUID spaceId, String paymentBatchId);

    @Query("""
            SELECT p FROM SpacePaymentEntity p
            JOIN FETCH p.member m
            LEFT JOIN FETCH m.user
            WHERE p.space.id = :spaceId AND p.month = :month
            """)
    List<SpacePaymentEntity> findBySpaceIdAndMonth(
            @Param("spaceId") UUID spaceId, @Param("month") String month);

    /**
     * Overdue candidates: due before business date, not PAID, active member.
     * Outstanding is derived in service (PAID = settled; no partial column yet).
     */
    @Query(
            value = """
                    SELECT p FROM SpacePaymentEntity p
                    JOIN p.member m
                    JOIN p.space s
                    WHERE p.space.id = :spaceId
                      AND p.dueDate < :businessDate
                      AND p.paymentStatus <> com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus.PAID
                      AND p.amount > 0
                      AND m.isActive = true
                      AND (:statusesEmpty = true OR p.paymentStatus IN :statuses)
                      AND (:paymentType IS NULL OR p.paymentType = :paymentType)
                    """,
            countQuery = """
                    SELECT COUNT(p) FROM SpacePaymentEntity p
                    JOIN p.member m
                    WHERE p.space.id = :spaceId
                      AND p.dueDate < :businessDate
                      AND p.paymentStatus <> com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus.PAID
                      AND p.amount > 0
                      AND m.isActive = true
                      AND (:statusesEmpty = true OR p.paymentStatus IN :statuses)
                      AND (:paymentType IS NULL OR p.paymentType = :paymentType)
                    """)
    Page<SpacePaymentEntity> findOverdueCandidates(
            @Param("spaceId") UUID spaceId,
            @Param("businessDate") java.time.LocalDate businessDate,
            @Param("statuses") Collection<SpacePaymentStatus> statuses,
            @Param("statusesEmpty") boolean statusesEmpty,
            @Param("paymentType") SpacePaymentType paymentType,
            Pageable pageable);

    @Query("""
            SELECT p FROM SpacePaymentEntity p
            JOIN FETCH p.member m
            LEFT JOIN FETCH m.user
            JOIN FETCH p.space s
            WHERE p.id IN :ids
            """)
    List<SpacePaymentEntity> findAllByIdInWithMemberAndSpace(@Param("ids") Collection<UUID> ids);

    List<SpacePaymentEntity> findByProofFileId(UUID proofFileId);
}
