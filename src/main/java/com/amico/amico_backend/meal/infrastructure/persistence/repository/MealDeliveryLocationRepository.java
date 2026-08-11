package com.amico.amico_backend.meal.infrastructure.persistence.repository;

import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealDeliveryLocationEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MealDeliveryLocationRepository extends JpaRepository<MealDeliveryLocationEntity, UUID> {

    List<MealDeliveryLocationEntity> findBySpaceIdAndActiveTrueOrderBySortOrderAscNameAsc(UUID spaceId);

    List<MealDeliveryLocationEntity> findBySpaceIdOrderBySortOrderAscNameAsc(UUID spaceId);

    Optional<MealDeliveryLocationEntity> findByIdAndSpaceId(UUID id, UUID spaceId);

    @Query("SELECT COALESCE(MAX(l.sortOrder), -1) FROM MealDeliveryLocationEntity l WHERE l.space.id = :spaceId")
    int findMaxSortOrderBySpaceId(@Param("spaceId") UUID spaceId);
}
