package com.acomi.acomi_backend.meal.infrastructure.persistence.repository;

import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealPollResponseEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MealPollResponseRepository extends JpaRepository<MealPollResponseEntity, UUID> {

    Optional<MealPollResponseEntity> findByPollIdAndMemberId(UUID pollId, UUID memberId);

    List<MealPollResponseEntity> findAllByPollIdAndMemberId(UUID pollId, UUID memberId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            """
            DELETE FROM MealPollResponseEntity r
            WHERE r.poll.id = :pollId AND r.member.id = :memberId
            """)
    void deleteByPollIdAndMemberId(@Param("pollId") UUID pollId, @Param("memberId") UUID memberId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM MealPollResponseEntity r WHERE r.poll.id = :pollId")
    void deleteByPollId(@Param("pollId") UUID pollId);

    List<MealPollResponseEntity> findByPollId(UUID pollId);

    @Query(
            """
            SELECT COUNT(DISTINCT r.member.id) FROM MealPollResponseEntity r
            WHERE r.poll.id = :pollId
            """)
    int countDistinctRespondingMembers(@Param("pollId") UUID pollId);

    @Query(
            """
            SELECT r FROM MealPollResponseEntity r
            JOIN FETCH r.member
            JOIN FETCH r.selectedOption
            WHERE r.poll.id = :pollId
            """)
    List<MealPollResponseEntity> findByPollIdWithMemberAndOption(@Param("pollId") UUID pollId);

    @Query(
            """
            SELECT r FROM MealPollResponseEntity r
            JOIN FETCH r.selectedOption o
            LEFT JOIN FETCH o.dailyMenuEntry e
            LEFT JOIN FETCH e.combo
            LEFT JOIN FETCH e.item
            JOIN FETCH r.poll p
            WHERE r.member.id = :memberId
              AND p.space.id = :spaceId
              AND p.pollDate BETWEEN :from AND :to
            """)
    List<MealPollResponseEntity> findForMemberInDateRange(
            @Param("memberId") UUID memberId,
            @Param("spaceId") UUID spaceId,
            @Param("from") java.time.LocalDate from,
            @Param("to") java.time.LocalDate to);
}
