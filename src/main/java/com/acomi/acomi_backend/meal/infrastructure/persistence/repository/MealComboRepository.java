package com.acomi.acomi_backend.meal.infrastructure.persistence.repository;

import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealComboEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MealComboRepository extends JpaRepository<MealComboEntity, UUID> {

    @Query("""
            SELECT c FROM MealComboEntity c
            WHERE c.isActive = true
              AND c.libraryListed = true
              AND c.owner.id = :ownerId
            ORDER BY c.name ASC
            """)
    List<MealComboEntity> findListedForOwner(@Param("ownerId") UUID ownerId);

    @Query("""
            SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
            FROM MealComboEntity c
            WHERE c.owner.id = :ownerId
              AND c.sampleKey = :sampleKey
              AND c.isActive = true
              AND c.libraryListed = true
            """)
    boolean existsListedSample(@Param("ownerId") UUID ownerId, @Param("sampleKey") String sampleKey);

    @Query("""
            SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
            FROM MealComboEntity c
            WHERE c.owner.id = :ownerId
              AND lower(c.name) = lower(:name)
              AND c.isActive = true
              AND c.libraryListed = true
            """)
    boolean existsListedName(@Param("ownerId") UUID ownerId, @Param("name") String name);

    @Query("""
            SELECT c FROM MealComboEntity c
            WHERE c.id = :id
              AND c.owner.id = :ownerId
            """)
    Optional<MealComboEntity> findByIdAndOwnerId(@Param("id") UUID id, @Param("ownerId") UUID ownerId);
}
