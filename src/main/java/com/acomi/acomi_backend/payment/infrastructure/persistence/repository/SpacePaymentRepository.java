package com.acomi.acomi_backend.payment.infrastructure.persistence.repository;

import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
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

    /**
     * Read-only charges for beds that are currently occupied.
     * Current-month rows plus security-deposit rows from the move-in month.
     * Does not create payments.
     */
    @Query("""
            SELECT DISTINCT p FROM SpacePaymentEntity p
            JOIN FETCH p.member
            JOIN FETCH p.space
            JOIN FETCH p.occupancy o
            WHERE p.space.id = :spaceId
              AND o.status = :activeStatus
              AND (p.month = :month OR p.paymentType = :depositType)
            """)
    List<SpacePaymentEntity> findActiveStayCharges(
            @Param("spaceId") UUID spaceId,
            @Param("month") String month,
            @Param("activeStatus") OccupancyStatus activeStatus,
            @Param("depositType") SpacePaymentType depositType);

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

    /**
     * Refunded history queue: full REFUNDED, partial (PAID with refundedAmount &gt; 0),
     * and REFUND child cash-out rows.
     */
    @Query(
            value = """
                    SELECT p FROM SpacePaymentEntity p
                    JOIN p.member m
                    WHERE p.space.id = :spaceId
                      AND p.month = :month
                      AND (:excludePaymentType IS NULL OR p.paymentType <> :excludePaymentType)
                      AND (
                        p.paymentStatus = com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus.REFUNDED
                        OR p.paymentCategory = com.acomi.acomi_backend.payment.domain.model.SpacePaymentCategory.REFUND
                        OR (
                          p.paymentStatus = com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus.PAID
                          AND COALESCE(p.refundedAmount, 0) > 0
                        )
                      )
                    """,
            countQuery = """
                    SELECT COUNT(p) FROM SpacePaymentEntity p
                    JOIN p.member m
                    WHERE p.space.id = :spaceId
                      AND p.month = :month
                      AND (:excludePaymentType IS NULL OR p.paymentType <> :excludePaymentType)
                      AND (
                        p.paymentStatus = com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus.REFUNDED
                        OR p.paymentCategory = com.acomi.acomi_backend.payment.domain.model.SpacePaymentCategory.REFUND
                        OR (
                          p.paymentStatus = com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus.PAID
                          AND COALESCE(p.refundedAmount, 0) > 0
                        )
                      )
                    """)
    Page<SpacePaymentEntity> searchRefundRelatedPaged(
            @Param("spaceId") UUID spaceId,
            @Param("month") String month,
            @Param("excludePaymentType") SpacePaymentType excludePaymentType,
            Pageable pageable);

    /** Paid history queue: collected with no refund activity (excludes partial refunds). */
    @Query(
            value = """
                    SELECT p FROM SpacePaymentEntity p
                    JOIN p.member m
                    WHERE p.space.id = :spaceId
                      AND p.month = :month
                      AND (:excludePaymentType IS NULL OR p.paymentType <> :excludePaymentType)
                      AND p.paymentStatus = com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus.PAID
                      AND p.paymentCategory <> com.acomi.acomi_backend.payment.domain.model.SpacePaymentCategory.REFUND
                      AND COALESCE(p.refundedAmount, 0) = 0
                    """,
            countQuery = """
                    SELECT COUNT(p) FROM SpacePaymentEntity p
                    JOIN p.member m
                    WHERE p.space.id = :spaceId
                      AND p.month = :month
                      AND (:excludePaymentType IS NULL OR p.paymentType <> :excludePaymentType)
                      AND p.paymentStatus = com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus.PAID
                      AND p.paymentCategory <> com.acomi.acomi_backend.payment.domain.model.SpacePaymentCategory.REFUND
                      AND COALESCE(p.refundedAmount, 0) = 0
                    """)
    Page<SpacePaymentEntity> searchPurePaidPaged(
            @Param("spaceId") UUID spaceId,
            @Param("month") String month,
            @Param("excludePaymentType") SpacePaymentType excludePaymentType,
            Pageable pageable);

    @Query("""
            SELECT COUNT(p) FROM SpacePaymentEntity p
            WHERE p.space.id = :spaceId AND p.month = :month
              AND (
                p.paymentStatus = com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus.REFUNDED
                OR p.paymentCategory = com.acomi.acomi_backend.payment.domain.model.SpacePaymentCategory.REFUND
                OR (
                  p.paymentStatus = com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus.PAID
                  AND COALESCE(p.refundedAmount, 0) > 0
                )
              )
            """)
    long countRefundRelated(@Param("spaceId") UUID spaceId, @Param("month") String month);

    @Query("""
            SELECT COUNT(p) FROM SpacePaymentEntity p
            WHERE p.space.id = :spaceId AND p.month = :month
              AND p.paymentStatus = com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus.PAID
              AND p.paymentCategory <> com.acomi.acomi_backend.payment.domain.model.SpacePaymentCategory.REFUND
              AND COALESCE(p.refundedAmount, 0) = 0
            """)
    long countPurePaid(@Param("spaceId") UUID spaceId, @Param("month") String month);

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

    List<SpacePaymentEntity> findAllBySpaceIdAndMemberIdAndMonthAndPaymentTypeAndPaymentCategory(
            UUID spaceId,
            UUID memberId,
            String month,
            SpacePaymentType paymentType,
            SpacePaymentCategory paymentCategory);

    /**
     * One generated period can contain a closed row and a later reissue. Prefer the open
     * obligation. A cancelled or refunded row is returned only when nothing open remains,
     * so automatic billing can refuse to recreate it.
     */
    default Optional<SpacePaymentEntity> findBySpaceIdAndMemberIdAndMonthAndPaymentTypeAndPaymentCategory(
            UUID spaceId,
            UUID memberId,
            String month,
            SpacePaymentType paymentType,
            SpacePaymentCategory paymentCategory) {
        List<SpacePaymentEntity> rows = findAllBySpaceIdAndMemberIdAndMonthAndPaymentTypeAndPaymentCategory(
                spaceId, memberId, month, paymentType, paymentCategory);
        if (rows == null || rows.isEmpty()) {
            return Optional.empty();
        }
        Optional<SpacePaymentEntity> open = rows.stream()
                .filter(row -> row.getPaymentStatus() != SpacePaymentStatus.CANCELLED
                        && row.getPaymentStatus() != SpacePaymentStatus.REFUNDED)
                .findFirst();
        return open.isPresent() ? open : Optional.of(rows.get(0));
    }

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
     * Tenant "payment requires update" actions are not limited to the month
     * the Action Center happens to be viewing.
     */
    @Query("""
            SELECT p FROM SpacePaymentEntity p
            JOIN FETCH p.member m
            LEFT JOIN FETCH m.user
            WHERE p.space.id = :spaceId
              AND p.paymentStatus = com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus.UPDATE_REQUESTED
            """)
    List<SpacePaymentEntity> findUpdateRequestedBySpaceId(@Param("spaceId") UUID spaceId);

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

    List<SpacePaymentEntity> findBySpaceIdAndMemberIdAndPaymentTypeAndPaymentCategory(
            UUID spaceId,
            UUID memberId,
            SpacePaymentType paymentType,
            SpacePaymentCategory paymentCategory);

    /**
     * Security-deposit charges and any recorded deposit refunds for one member.
     * Used to build the member deposit summary without loading the full ledger.
     */
    @Query("""
            SELECT p FROM SpacePaymentEntity p
            WHERE p.space.id = :spaceId
              AND p.member.id = :memberId
              AND (p.paymentType = com.acomi.acomi_backend.payment.domain.model.SpacePaymentType.DEPOSIT
                   OR p.paymentCategory = com.acomi.acomi_backend.payment.domain.model.SpacePaymentCategory.REFUND)
            """)
    List<SpacePaymentEntity> findDepositLedgerRows(
            @Param("spaceId") UUID spaceId, @Param("memberId") UUID memberId);

    Optional<SpacePaymentEntity>
            findByOccupancy_IdAndPaymentTypeAndPaymentCategoryAndBillingPeriodStartAndBillingPeriodEnd(
                    UUID occupancyId,
                    SpacePaymentType paymentType,
                    SpacePaymentCategory paymentCategory,
                    java.time.LocalDate billingPeriodStart,
                    java.time.LocalDate billingPeriodEnd);

    List<SpacePaymentEntity> findByOccupancy_Id(UUID occupancyId);

    List<SpacePaymentEntity> findByRelatedPayment_Id(UUID relatedPaymentId);

    /**
     * OPEN_ENDED daily-rate RENT/MONTHLY rows — used for read-only premature-bill reconciliation.
     * Does not mutate data.
     */
    @Query("""
            SELECT p FROM SpacePaymentEntity p
            JOIN FETCH p.member
            JOIN FETCH p.space
            JOIN FETCH p.occupancy o
            WHERE p.paymentType = com.acomi.acomi_backend.payment.domain.model.SpacePaymentType.RENT
              AND p.paymentCategory = com.acomi.acomi_backend.payment.domain.model.SpacePaymentCategory.MONTHLY
              AND o.stayKind = com.acomi.acomi_backend.occupancy.domain.model.StayKind.OPEN_ENDED
              AND o.pricingBasis = 'DAILY_RATE'
            ORDER BY p.createdAt ASC
            """)
    List<SpacePaymentEntity> findOpenEndedDailyRateMonthlyRentPayments();

    /**
     * Automatic rent rows that never received an outbox event. Used only to retry
     * notification enqueue after a crash between payment insert and outbox insert.
     */
    @Query("""
            SELECT p FROM SpacePaymentEntity p
            JOIN FETCH p.member m
            LEFT JOIN FETCH m.user
            JOIN FETCH p.space
            WHERE p.remarks = :remarks
              AND p.paymentType = com.acomi.acomi_backend.payment.domain.model.SpacePaymentType.RENT
              AND p.paymentCategory = com.acomi.acomi_backend.payment.domain.model.SpacePaymentCategory.MONTHLY
              AND p.paymentStatus = com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus.PENDING
              AND NOT EXISTS (
                  SELECT n.id FROM MonthlyBillingNotificationEntity n
                  WHERE n.paymentId = p.id AND n.eventType = :eventType)
            """)
    List<SpacePaymentEntity> findPendingAutomaticRentWithoutNotification(
            @Param("remarks") String remarks, @Param("eventType") String eventType, Pageable pageable);
}
