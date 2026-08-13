package com.acomi.acomi_backend.meal.infrastructure.persistence.repository;

import com.acomi.acomi_backend.meal.domain.model.FoodScope;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.FoodCategoryEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FoodCategoryRepository extends JpaRepository<FoodCategoryEntity, UUID> {

    @Query("""
            SELECT c FROM FoodCategoryEntity c
            WHERE c.isActive = true
              AND (
                  (c.scope = com.acomi.acomi_backend.meal.domain.model.FoodScope.GLOBAL
                   AND c.space IS NULL
                   AND NOT EXISTS (
                       SELECT 1 FROM SpaceFoodCategorySettingsEntity s
                       WHERE s.spaceId = :spaceId AND s.categoryId = c.id AND s.isEnabled = false))
                  OR (c.scope = com.acomi.acomi_backend.meal.domain.model.FoodScope.SPACE
                      AND c.space.id = :spaceId))
            ORDER BY c.sortOrder ASC, c.name ASC
            """)
    List<FoodCategoryEntity> findVisibleForSpace(@Param("spaceId") UUID spaceId);

    @Query("""
            SELECT COUNT(c) FROM FoodCategoryEntity c
            WHERE c.isActive = true
              AND c.scope = com.acomi.acomi_backend.meal.domain.model.FoodScope.GLOBAL
              AND c.space IS NULL
            """)
    long countGlobalActive();

    Optional<FoodCategoryEntity> findByIdAndSpaceId(UUID id, UUID spaceId);

    @Query("""
            SELECT c FROM FoodCategoryEntity c
            WHERE c.id = :id
              AND c.scope = com.acomi.acomi_backend.meal.domain.model.FoodScope.SPACE
              AND c.space.id = :spaceId
            """)
    Optional<FoodCategoryEntity> findSpaceCategory(@Param("id") UUID id, @Param("spaceId") UUID spaceId);

    boolean existsByScopeAndName(FoodScope scope, String name);
}
