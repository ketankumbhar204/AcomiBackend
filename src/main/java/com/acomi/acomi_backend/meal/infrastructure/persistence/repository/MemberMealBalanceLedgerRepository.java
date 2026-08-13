package com.acomi.acomi_backend.meal.infrastructure.persistence.repository;

import com.acomi.acomi_backend.meal.domain.model.MealBalanceLedgerEntryType;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MemberMealBalanceLedgerEntryEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberMealBalanceLedgerRepository
        extends JpaRepository<MemberMealBalanceLedgerEntryEntity, UUID> {

    Optional<MemberMealBalanceLedgerEntryEntity> findByIdempotencyKey(String idempotencyKey);

    @Query(
            """
            SELECT COALESCE(SUM(e.amount), 0) FROM MemberMealBalanceLedgerEntryEntity e
            JOIN e.balance b
            WHERE b.space.id = :spaceId
              AND e.entryType = :entryType
              AND e.createdAt >= :from
              AND e.createdAt < :to
            """)
    BigDecimal sumAmountBySpaceAndTypeInRange(
            @Param("spaceId") UUID spaceId,
            @Param("entryType") MealBalanceLedgerEntryType entryType,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query(
            """
            SELECT COALESCE(SUM(e.amount), 0) FROM MemberMealBalanceLedgerEntryEntity e
            JOIN e.balance b
            WHERE b.space.id = :spaceId
              AND b.member.id = :memberId
              AND e.entryType = :entryType
              AND e.createdAt >= :from
              AND e.createdAt < :to
            """)
    BigDecimal sumAmountByMemberAndTypeInRange(
            @Param("spaceId") UUID spaceId,
            @Param("memberId") UUID memberId,
            @Param("entryType") MealBalanceLedgerEntryType entryType,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query(
            """
            SELECT COALESCE(SUM(e.paidAmount), 0) FROM MemberMealBalanceLedgerEntryEntity e
            JOIN e.balance b
            WHERE b.space.id = :spaceId
              AND e.entryType = :entryType
              AND e.createdAt >= :from
              AND e.createdAt < :to
            """)
    BigDecimal sumPaidAmountBySpaceAndTypeInRange(
            @Param("spaceId") UUID spaceId,
            @Param("entryType") MealBalanceLedgerEntryType entryType,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query(
            """
            SELECT COALESCE(SUM(e.paidAmount), 0) FROM MemberMealBalanceLedgerEntryEntity e
            JOIN e.balance b
            WHERE b.space.id = :spaceId
              AND b.member.id = :memberId
              AND e.entryType = :entryType
              AND e.createdAt >= :from
              AND e.createdAt < :to
            """)
    BigDecimal sumPaidAmountByMemberAndTypeInRange(
            @Param("spaceId") UUID spaceId,
            @Param("memberId") UUID memberId,
            @Param("entryType") MealBalanceLedgerEntryType entryType,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    List<MemberMealBalanceLedgerEntryEntity> findByBalanceIdAndPollDateOrderByCreatedAtAsc(
            UUID balanceId, java.time.LocalDate pollDate);

    Optional<MemberMealBalanceLedgerEntryEntity> findFirstByBalanceIdAndEntryTypeOrderByCreatedAtDesc(
            UUID balanceId, MealBalanceLedgerEntryType entryType);

    Optional<MemberMealBalanceLedgerEntryEntity> findFirstByBalanceIdAndEntryTypeOrderByCreatedAtAsc(
            UUID balanceId, MealBalanceLedgerEntryType entryType);

    @Query(
            """
            SELECT e FROM MemberMealBalanceLedgerEntryEntity e
            WHERE e.balance.id = :balanceId
              AND e.entryType = :entryType
              AND e.createdAt > :after
            ORDER BY e.createdAt ASC
            LIMIT 1
            """)
    Optional<MemberMealBalanceLedgerEntryEntity> findFirstByBalanceIdAndEntryTypeAndCreatedAtAfterOrderByCreatedAtAsc(
            @Param("balanceId") UUID balanceId,
            @Param("entryType") MealBalanceLedgerEntryType entryType,
            @Param("after") LocalDateTime after);

    @Query(
            """
            SELECT e FROM MemberMealBalanceLedgerEntryEntity e
            JOIN e.balance b
            WHERE b.space.id = :spaceId
              AND b.member.id = :memberId
              AND e.createdAt >= :from
              AND e.createdAt < :to
            ORDER BY e.createdAt ASC
            """)
    List<MemberMealBalanceLedgerEntryEntity> findByMemberAndCreatedAtBetweenOrderByCreatedAtAsc(
            @Param("spaceId") UUID spaceId,
            @Param("memberId") UUID memberId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query(
            """
            SELECT COUNT(e) FROM MemberMealBalanceLedgerEntryEntity e
            WHERE e.balance.id = :balanceId
              AND e.entryType = :entryType
              AND e.createdAt < :before
            """)
    long countByBalanceIdAndEntryTypeAndCreatedAtBefore(
            @Param("balanceId") UUID balanceId,
            @Param("entryType") MealBalanceLedgerEntryType entryType,
            @Param("before") LocalDateTime before);

    Optional<MemberMealBalanceLedgerEntryEntity> findFirstByBalanceIdAndEntryTypeAndCreatedAtBeforeOrderByCreatedAtDesc(
            UUID balanceId, MealBalanceLedgerEntryType entryType, LocalDateTime before);

    @Query(
            """
            SELECT COALESCE(SUM(e.amount), 0) FROM MemberMealBalanceLedgerEntryEntity e
            WHERE e.balance.id = :balanceId
              AND e.entryType = :entryType
              AND e.createdAt >= :from
            """)
    BigDecimal sumAmountByBalanceAndTypeSince(
            @Param("balanceId") UUID balanceId,
            @Param("entryType") MealBalanceLedgerEntryType entryType,
            @Param("from") LocalDateTime from);

    @Query(
            """
            SELECT e FROM MemberMealBalanceLedgerEntryEntity e
            JOIN e.balance b
            WHERE b.space.id = :spaceId
              AND b.member.id = :memberId
              AND e.entryType IN :entryTypes
            ORDER BY e.createdAt DESC
            """)
    List<MemberMealBalanceLedgerEntryEntity> findSubscriptionEventsByMember(
            @Param("spaceId") UUID spaceId,
            @Param("memberId") UUID memberId,
            @Param("entryTypes") Collection<MealBalanceLedgerEntryType> entryTypes);

    @Query(
            """
            SELECT COALESCE(SUM(e.amount), 0) FROM MemberMealBalanceLedgerEntryEntity e
            WHERE e.balance.id = :balanceId
              AND e.entryType = :entryType
            """)
    BigDecimal sumAmountByBalanceIdAndEntryType(
            @Param("balanceId") UUID balanceId, @Param("entryType") MealBalanceLedgerEntryType entryType);

    @Query(
            """
            SELECT COALESCE(SUM(e.paidAmount), 0) FROM MemberMealBalanceLedgerEntryEntity e
            WHERE e.balance.id = :balanceId
              AND e.entryType = :entryType
            """)
    BigDecimal sumPaidAmountByBalanceIdAndEntryType(
            @Param("balanceId") UUID balanceId, @Param("entryType") MealBalanceLedgerEntryType entryType);

    @Query(
            """
            SELECT COALESCE(SUM(e.amount), 0) FROM MemberMealBalanceLedgerEntryEntity e
            WHERE e.balance.id = :balanceId
              AND e.entryType = :entryType
              AND (:since IS NULL OR e.createdAt >= :since)
            """)
    BigDecimal sumAmountByBalanceIdAndEntryTypeSince(
            @Param("balanceId") UUID balanceId,
            @Param("entryType") MealBalanceLedgerEntryType entryType,
            @Param("since") LocalDateTime since);

    @Query(
            """
            SELECT COALESCE(SUM(e.paidAmount), 0) FROM MemberMealBalanceLedgerEntryEntity e
            WHERE e.balance.id = :balanceId
              AND e.entryType = :entryType
              AND (:since IS NULL OR e.createdAt >= :since)
            """)
    BigDecimal sumPaidAmountByBalanceIdAndEntryTypeSince(
            @Param("balanceId") UUID balanceId,
            @Param("entryType") MealBalanceLedgerEntryType entryType,
            @Param("since") LocalDateTime since);

    @Query(
            """
            SELECT e FROM MemberMealBalanceLedgerEntryEntity e
            JOIN e.balance b
            WHERE b.space.id = :spaceId
              AND b.member.id = :memberId
              AND e.entryType IN :entryTypes
            ORDER BY e.createdAt ASC
            """)
    List<MemberMealBalanceLedgerEntryEntity> findSubscriptionEventsByMemberChronological(
            @Param("spaceId") UUID spaceId,
            @Param("memberId") UUID memberId,
            @Param("entryTypes") Collection<MealBalanceLedgerEntryType> entryTypes);
}
