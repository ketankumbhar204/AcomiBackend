package com.countin.countin_backend.meal.infrastructure.persistence.repository;

import com.countin.countin_backend.meal.domain.model.DailyMenuStatus;
import com.countin.countin_backend.meal.domain.model.MealType;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.DailyMenuEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DailyMenuRepository extends JpaRepository<DailyMenuEntity, UUID> {

    @Query("""
            SELECT d FROM DailyMenuEntity d
            WHERE d.space.id = :spaceId
              AND d.menuDate BETWEEN :from AND :to
              AND d.isDeleted = false
              AND (:publishedOnly = false OR d.status IN (
                    com.countin.countin_backend.meal.domain.model.DailyMenuStatus.PUBLISHED,
                    com.countin.countin_backend.meal.domain.model.DailyMenuStatus.MODIFIED))
            ORDER BY d.menuDate ASC, d.mealType ASC
            """)
    List<DailyMenuEntity> findBySpaceAndDateRange(
            @Param("spaceId") UUID spaceId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("publishedOnly") boolean publishedOnly);

    @Query("""
            SELECT d FROM DailyMenuEntity d
            WHERE d.space.id = :spaceId
              AND d.menuDate = :menuDate
              AND d.mealType = :mealType
              AND d.isDeleted = false
            """)
    Optional<DailyMenuEntity> findBySpaceDateAndType(
            @Param("spaceId") UUID spaceId,
            @Param("menuDate") LocalDate menuDate,
            @Param("mealType") MealType mealType);

    @Query("""
            SELECT d FROM DailyMenuEntity d
            WHERE d.space.id = :spaceId
              AND d.menuDate = :menuDate
              AND d.isDeleted = false
              AND (:publishedOnly = false OR d.status IN (
                    com.countin.countin_backend.meal.domain.model.DailyMenuStatus.PUBLISHED,
                    com.countin.countin_backend.meal.domain.model.DailyMenuStatus.MODIFIED))
            ORDER BY d.mealType ASC
            """)
    List<DailyMenuEntity> findBySpaceAndDate(
            @Param("spaceId") UUID spaceId,
            @Param("menuDate") LocalDate menuDate,
            @Param("publishedOnly") boolean publishedOnly,
            @Param("status") DailyMenuStatus status);
}
