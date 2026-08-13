package com.acomi.acomi_backend.meal.infrastructure.persistence.repository;

import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.SpaceFoodCategorySettingsEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.SpaceFoodCategorySettingsEntity.Pk;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpaceFoodCategorySettingsRepository extends JpaRepository<SpaceFoodCategorySettingsEntity, Pk> {

    Optional<SpaceFoodCategorySettingsEntity> findBySpaceIdAndCategoryId(UUID spaceId, UUID categoryId);
}
