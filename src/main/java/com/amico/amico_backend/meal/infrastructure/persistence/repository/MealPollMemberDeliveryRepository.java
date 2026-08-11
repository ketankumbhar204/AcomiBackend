package com.amico.amico_backend.meal.infrastructure.persistence.repository;

import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealPollMemberDeliveryEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MealPollMemberDeliveryRepository extends JpaRepository<MealPollMemberDeliveryEntity, UUID> {

    @Query(
            """
            SELECT d FROM MealPollMemberDeliveryEntity d
            JOIN FETCH d.deliveryLocation
            WHERE d.poll.id = :pollId AND d.member.id = :memberId
            """)
    Optional<MealPollMemberDeliveryEntity> findByPollIdAndMemberId(
            @Param("pollId") UUID pollId, @Param("memberId") UUID memberId);

    @Query(
            """
            SELECT d FROM MealPollMemberDeliveryEntity d
            JOIN FETCH d.deliveryLocation
            JOIN FETCH d.member
            WHERE d.poll.id = :pollId
            """)
    List<MealPollMemberDeliveryEntity> findByPollIdWithLocation(@Param("pollId") UUID pollId);

    @Query(
            """
            SELECT d FROM MealPollMemberDeliveryEntity d
            JOIN FETCH d.deliveryLocation
            JOIN FETCH d.poll p
            WHERE d.member.id = :memberId
              AND p.space.id = :spaceId
              AND p.pollDate BETWEEN :from AND :to
            """)
    List<MealPollMemberDeliveryEntity> findForMemberInDateRange(
            @Param("memberId") UUID memberId,
            @Param("spaceId") UUID spaceId,
            @Param("from") java.time.LocalDate from,
            @Param("to") java.time.LocalDate to);

    void deleteByPollIdAndMemberId(UUID pollId, UUID memberId);
}
