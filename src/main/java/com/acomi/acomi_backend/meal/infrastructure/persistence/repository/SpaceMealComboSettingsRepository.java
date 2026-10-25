package com.acomi.acomi_backend.meal.infrastructure.persistence.repository;

import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.SpaceMealComboSettingsEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpaceMealComboSettingsRepository
        extends JpaRepository<SpaceMealComboSettingsEntity, SpaceMealComboSettingsEntity.Pk> {

    Optional<SpaceMealComboSettingsEntity> findBySpaceIdAndComboId(UUID spaceId, UUID comboId);

    List<SpaceMealComboSettingsEntity> findAllBySpaceId(UUID spaceId);
}
