package com.acomi.acomi_backend.meal.infrastructure.persistence.repository;

import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.SpaceFoodItemSettingsEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.SpaceFoodItemSettingsEntity.Pk;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpaceFoodItemSettingsRepository extends JpaRepository<SpaceFoodItemSettingsEntity, Pk> {

    Optional<SpaceFoodItemSettingsEntity> findBySpaceIdAndItemId(UUID spaceId, UUID itemId);

    List<SpaceFoodItemSettingsEntity> findAllBySpaceId(UUID spaceId);
}
