package com.acomi.acomi_backend.meal.infrastructure.persistence.repository;

import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealParticipationHistoryEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MealParticipationHistoryRepository
        extends JpaRepository<MealParticipationHistoryEntity, UUID> {

    List<MealParticipationHistoryEntity> findByParticipationIdOrderByChangedAtDesc(UUID participationId);
}
