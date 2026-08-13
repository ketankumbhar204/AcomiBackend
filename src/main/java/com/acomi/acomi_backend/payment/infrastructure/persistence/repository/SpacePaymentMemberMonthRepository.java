package com.acomi.acomi_backend.payment.infrastructure.persistence.repository;

import com.acomi.acomi_backend.dashboard.domain.model.MemberPaymentStatus;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.SpacePaymentMemberMonthEntity;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpacePaymentMemberMonthRepository
        extends JpaRepository<SpacePaymentMemberMonthEntity, UUID> {

    long countBySpaceIdAndMonth(UUID spaceId, String month);

    Optional<SpacePaymentMemberMonthEntity> findBySpaceIdAndMonthAndMemberId(
            UUID spaceId, String month, UUID memberId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM SpacePaymentMemberMonthEntity e WHERE e.spaceId = :spaceId AND e.month = :month")
    int deleteBySpaceIdAndMonth(@Param("spaceId") UUID spaceId, @Param("month") String month);

    @Query("""
            SELECT e FROM SpacePaymentMemberMonthEntity e
            WHERE e.spaceId = :spaceId
              AND e.month = :month
              AND (:searchBlank = TRUE OR LOWER(e.memberName) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (
                    :statusesEmpty = TRUE
                    OR e.status IN :statuses
                    OR (
                         :matchUnderReviewAmounts = TRUE
                         AND e.underReview IS NOT NULL
                         AND e.underReview > 0
                         AND (e.pending IS NULL OR e.pending = 0)
                       )
                  )
              AND (
                    :matchCollectedAmounts = FALSE
                    OR (e.collected IS NOT NULL AND e.collected > 0)
                  )
              AND (
                    :excludeCoveredUnderReview = FALSE
                    OR NOT (
                         e.underReview IS NOT NULL
                         AND e.underReview > 0
                         AND (e.pending IS NULL OR e.pending = 0)
                       )
                  )
            """)
    Page<SpacePaymentMemberMonthEntity> searchPaged(
            @Param("spaceId") UUID spaceId,
            @Param("month") String month,
            @Param("search") String search,
            @Param("searchBlank") boolean searchBlank,
            @Param("statuses") Collection<MemberPaymentStatus> statuses,
            @Param("statusesEmpty") boolean statusesEmpty,
            @Param("matchUnderReviewAmounts") boolean matchUnderReviewAmounts,
            @Param("matchCollectedAmounts") boolean matchCollectedAmounts,
            @Param("excludeCoveredUnderReview") boolean excludeCoveredUnderReview,
            Pageable pageable);

    @Query("""
            SELECT COUNT(e) FROM SpacePaymentMemberMonthEntity e
            WHERE e.spaceId = :spaceId
              AND e.month = :month
              AND e.status IN :statuses
            """)
    long countBySpaceIdAndMonthAndStatusIn(
            @Param("spaceId") UUID spaceId,
            @Param("month") String month,
            @Param("statuses") Collection<MemberPaymentStatus> statuses);
}
