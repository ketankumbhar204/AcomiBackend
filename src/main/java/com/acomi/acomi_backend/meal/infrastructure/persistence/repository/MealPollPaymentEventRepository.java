package com.acomi.acomi_backend.meal.infrastructure.persistence.repository;

import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealPollPaymentEventEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MealPollPaymentEventRepository extends JpaRepository<MealPollPaymentEventEntity, UUID> {

    List<MealPollPaymentEventEntity> findBySpaceIdAndMemberIdAndPollDateBetweenOrderByCreatedAtAsc(
            UUID spaceId, UUID memberId, LocalDate from, LocalDate to);
}
