package com.amico.amico_backend.meal.infrastructure.persistence.repository;

import com.amico.amico_backend.meal.infrastructure.persistence.entity.FoodItemEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FoodItemRepository extends JpaRepository<FoodItemEntity, UUID> {

    @Query("""
            SELECT i FROM FoodItemEntity i
            JOIN FETCH i.category c
            WHERE i.isActive = true
              AND (
                  (i.scope = com.amico.amico_backend.meal.domain.model.FoodScope.GLOBAL
                   AND i.space IS NULL
                   AND NOT EXISTS (
                       SELECT 1 FROM SpaceFoodItemSettingsEntity s
                       WHERE s.spaceId = :spaceId AND s.itemId = i.id AND s.isEnabled = false))
                  OR (i.scope = com.amico.amico_backend.meal.domain.model.FoodScope.SPACE
                      AND i.space.id = :spaceId))
            ORDER BY c.sortOrder ASC, i.name ASC
            """)
    List<FoodItemEntity> findAllVisibleForSpace(@Param("spaceId") UUID spaceId);

    @Query("""
            SELECT i FROM FoodItemEntity i
            JOIN FETCH i.category c
            WHERE i.isActive = true
              AND (
                  (i.scope = com.amico.amico_backend.meal.domain.model.FoodScope.GLOBAL
                   AND i.space IS NULL
                   AND NOT EXISTS (
                       SELECT 1 FROM SpaceFoodItemSettingsEntity s
                       WHERE s.spaceId = :spaceId AND s.itemId = i.id AND s.isEnabled = false))
                  OR (i.scope = com.amico.amico_backend.meal.domain.model.FoodScope.SPACE
                      AND i.space.id = :spaceId))
              AND i.category.id = :categoryId
            ORDER BY c.sortOrder ASC, i.name ASC
            """)
    List<FoodItemEntity> findVisibleForSpaceInCategory(
            @Param("spaceId") UUID spaceId, @Param("categoryId") UUID categoryId);

    @Query("""
            SELECT i FROM FoodItemEntity i
            JOIN FETCH i.category
            WHERE i.id = :id
            """)
    Optional<FoodItemEntity> findByIdWithCategory(@Param("id") UUID id);

    @Query("""
            SELECT i FROM FoodItemEntity i
            JOIN FETCH i.category
            WHERE i.id = :id
              AND i.scope = com.amico.amico_backend.meal.domain.model.FoodScope.SPACE
              AND i.space.id = :spaceId
            """)
    Optional<FoodItemEntity> findSpaceItem(@Param("id") UUID id, @Param("spaceId") UUID spaceId);

    @Query("""
            SELECT i FROM FoodItemEntity i
            WHERE i.scope = com.amico.amico_backend.meal.domain.model.FoodScope.GLOBAL
              AND i.name = :name
              AND i.isActive = true
            """)
    Optional<FoodItemEntity> findGlobalByName(@Param("name") String name);

    @Query("""
            SELECT i FROM FoodItemEntity i
            WHERE i.isActive = true
              AND i.category.id = :categoryId
              AND i.scope = com.amico.amico_backend.meal.domain.model.FoodScope.SPACE
              AND i.space.id = :spaceId
            """)
    List<FoodItemEntity> findActiveSpaceItemsInCategory(
            @Param("spaceId") UUID spaceId, @Param("categoryId") UUID categoryId);

    @Query("""
            SELECT COUNT(i) FROM FoodItemEntity i
            WHERE i.isActive = true
              AND i.category.id = :categoryId
              AND (
                  (i.scope = com.amico.amico_backend.meal.domain.model.FoodScope.GLOBAL
                   AND i.space IS NULL
                   AND NOT EXISTS (
                       SELECT 1 FROM SpaceFoodItemSettingsEntity s
                       WHERE s.spaceId = :spaceId AND s.itemId = i.id AND s.isEnabled = false))
                  OR (i.scope = com.amico.amico_backend.meal.domain.model.FoodScope.SPACE
                      AND i.space.id = :spaceId))
            """)
    long countEnabledForSpaceInCategory(@Param("spaceId") UUID spaceId, @Param("categoryId") UUID categoryId);

    @Query("""
            SELECT COUNT(i) FROM FoodItemEntity i
            WHERE i.isActive = true
              AND i.scope = com.amico.amico_backend.meal.domain.model.FoodScope.GLOBAL
              AND i.space IS NULL
            """)
    long countGlobalActive();

    interface CategoryItemCount {
        UUID getCategoryId();
        long getItemCount();
    }

    @Query("""
            SELECT i.category.id AS categoryId, COUNT(i) AS itemCount
            FROM FoodItemEntity i
            WHERE i.isActive = true
              AND (
                  (i.scope = com.amico.amico_backend.meal.domain.model.FoodScope.GLOBAL
                   AND i.space IS NULL
                   AND NOT EXISTS (
                       SELECT 1 FROM SpaceFoodItemSettingsEntity s
                       WHERE s.spaceId = :spaceId AND s.itemId = i.id AND s.isEnabled = false))
                  OR (i.scope = com.amico.amico_backend.meal.domain.model.FoodScope.SPACE
                      AND i.space.id = :spaceId))
            GROUP BY i.category.id
            """)
    List<CategoryItemCount> countVisibleItemsGroupedByCategory(@Param("spaceId") UUID spaceId);
}
