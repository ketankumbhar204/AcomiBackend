package com.amico.amico_backend.meal.infrastructure.persistence.repository;

import com.amico.amico_backend.meal.domain.model.MealParticipationStatus;
import com.amico.amico_backend.meal.domain.model.MealPlanCode;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealParticipationEntity;
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
public interface MealParticipationRepository extends JpaRepository<MealParticipationEntity, UUID> {

    @Query("""
            SELECT p FROM MealParticipationEntity p
            JOIN FETCH p.member m
            JOIN FETCH p.mealPlan
            WHERE p.id = :id AND p.space.id = :spaceId
            """)
    Optional<MealParticipationEntity> findByIdAndSpaceId(@Param("id") UUID id, @Param("spaceId") UUID spaceId);

    @Query("""
            SELECT p FROM MealParticipationEntity p
            JOIN FETCH p.mealPlan
            WHERE p.space.id = :spaceId AND p.member.id = :memberId AND p.status = :status
            """)
    Optional<MealParticipationEntity> findBySpaceIdAndMemberIdAndStatus(
            @Param("spaceId") UUID spaceId,
            @Param("memberId") UUID memberId,
            @Param("status") MealParticipationStatus status);

    @Query(
            value = """
            SELECT p FROM MealParticipationEntity p
            JOIN p.member m
            JOIN p.mealPlan
            WHERE p.space.id = :spaceId
              AND (:status IS NULL OR p.status = :status)
              AND (:planCode IS NULL OR p.mealPlan.code = :planCode)
              AND (:memberId IS NULL OR p.member.id = :memberId)
              AND (
                  :search IS NULL OR :search = ''
                  OR LOWER(m.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                  OR m.mobileNumber LIKE CONCAT('%', :search, '%')
              )
            ORDER BY m.fullName ASC
            """,
            countQuery = """
            SELECT COUNT(p) FROM MealParticipationEntity p
            JOIN p.member m
            JOIN p.mealPlan
            WHERE p.space.id = :spaceId
              AND (:status IS NULL OR p.status = :status)
              AND (:planCode IS NULL OR p.mealPlan.code = :planCode)
              AND (:memberId IS NULL OR p.member.id = :memberId)
              AND (
                  :search IS NULL OR :search = ''
                  OR LOWER(m.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                  OR m.mobileNumber LIKE CONCAT('%', :search, '%')
              )
            """)
    Page<MealParticipationEntity> search(
            @Param("spaceId") UUID spaceId,
            @Param("status") MealParticipationStatus status,
            @Param("planCode") MealPlanCode planCode,
            @Param("memberId") UUID memberId,
            @Param("search") String search,
            Pageable pageable);

    @Query("""
            SELECT p FROM MealParticipationEntity p
            JOIN FETCH p.member m
            JOIN FETCH p.mealPlan
            WHERE p.space.id = :spaceId
              AND p.status = com.amico.amico_backend.meal.domain.model.MealParticipationStatus.ACTIVE
            """)
    List<MealParticipationEntity> findAllActiveBySpaceId(@Param("spaceId") UUID spaceId);

    @Query("""
            SELECT p FROM MealParticipationEntity p
            JOIN FETCH p.member m
            JOIN FETCH p.mealPlan
            WHERE p.space.id = :spaceId
              AND p.status <> com.amico.amico_backend.meal.domain.model.MealParticipationStatus.STOPPED
            """)
    List<MealParticipationEntity> findAllNonStoppedBySpaceId(@Param("spaceId") UUID spaceId);

    boolean existsBySpaceIdAndMemberIdAndStatus(UUID spaceId, UUID memberId, MealParticipationStatus status);
}
