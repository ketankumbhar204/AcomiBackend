package com.amico.amico_backend.meal.infrastructure.persistence.repository;

import com.amico.amico_backend.meal.domain.model.MealPollStatus;
import com.amico.amico_backend.meal.domain.model.MealType;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealPollEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MealPollRepository extends JpaRepository<MealPollEntity, UUID> {

    Optional<MealPollEntity> findBySpaceIdAndPollDateAndMealType(UUID spaceId, LocalDate pollDate, MealType mealType);

    List<MealPollEntity> findBySpaceIdAndPollDateOrderByMealTypeAsc(UUID spaceId, LocalDate pollDate);

    List<MealPollEntity> findBySpaceIdAndPollDateBetweenOrderByPollDateAscMealTypeAsc(
            UUID spaceId, LocalDate from, LocalDate to);

    @Query(
            """
            SELECT p FROM MealPollEntity p
            JOIN FETCH p.space
            WHERE p.status = :status
              AND p.pollCloseAt IS NOT NULL
              AND p.pollCloseAt <= :now
            """)
    List<MealPollEntity> findOpenDueForAutoClose(
            @Param("status") MealPollStatus status, @Param("now") LocalDateTime now);
}
