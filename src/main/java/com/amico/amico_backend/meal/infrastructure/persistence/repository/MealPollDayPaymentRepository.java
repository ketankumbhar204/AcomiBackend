package com.amico.amico_backend.meal.infrastructure.persistence.repository;

import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealPollDayPaymentEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MealPollDayPaymentRepository extends JpaRepository<MealPollDayPaymentEntity, UUID> {

    List<MealPollDayPaymentEntity> findByPaymentBatchId(String paymentBatchId);

    Optional<MealPollDayPaymentEntity> findBySpaceIdAndMemberIdAndPollDate(
            UUID spaceId, UUID memberId, LocalDate pollDate);

    List<MealPollDayPaymentEntity> findBySpaceIdAndPollDate(UUID spaceId, LocalDate pollDate);

    @Query(
            """
            SELECT p FROM MealPollDayPaymentEntity p
            WHERE p.member.id = :memberId
              AND p.space.id = :spaceId
              AND p.pollDate BETWEEN :from AND :to
            """)
    List<MealPollDayPaymentEntity> findForMemberInDateRange(
            @Param("memberId") UUID memberId,
            @Param("spaceId") UUID spaceId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query(
            """
            SELECT p FROM MealPollDayPaymentEntity p
            JOIN FETCH p.member
            JOIN FETCH p.space
            WHERE p.space.id = :spaceId
              AND p.pollDate BETWEEN :from AND :to
              AND p.paymentStatus = com.amico.amico_backend.meal.domain.model.MealPollPaymentStatus.PENDING_APPROVAL
            ORDER BY p.pollDate ASC, p.member.fullName ASC
            """)
    List<MealPollDayPaymentEntity> findPendingApprovalInDateRange(
            @Param("spaceId") UUID spaceId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
