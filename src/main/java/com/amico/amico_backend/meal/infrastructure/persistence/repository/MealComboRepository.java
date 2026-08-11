package com.amico.amico_backend.meal.infrastructure.persistence.repository;

import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealComboEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MealComboRepository extends JpaRepository<MealComboEntity, UUID> {

    List<MealComboEntity> findBySpaceIdAndIsActiveTrueOrderByNameAsc(UUID spaceId);

    boolean existsBySpaceIdAndNameIgnoreCaseAndIsActiveTrue(UUID spaceId, String name);

    Optional<MealComboEntity> findByIdAndSpaceId(UUID id, UUID spaceId);
}
