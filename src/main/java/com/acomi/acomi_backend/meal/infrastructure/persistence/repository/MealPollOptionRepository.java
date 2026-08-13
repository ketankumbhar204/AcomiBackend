package com.acomi.acomi_backend.meal.infrastructure.persistence.repository;

import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealPollOptionEntity;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MealPollOptionRepository extends JpaRepository<MealPollOptionEntity, UUID> {

    @Query(
            """
            SELECT o FROM MealPollOptionEntity o
            LEFT JOIN FETCH o.dailyMenuEntry e
            LEFT JOIN FETCH e.combo
            LEFT JOIN FETCH e.item
            WHERE o.poll.id = :pollId
            ORDER BY o.sortOrder ASC
            """)
    List<MealPollOptionEntity> findByPollIdWithEntriesOrderBySortOrderAsc(@Param("pollId") UUID pollId);

    List<MealPollOptionEntity> findByPollIdOrderBySortOrderAsc(UUID pollId);

    @Query("""
            SELECT DISTINCT o.dailyMenuEntry.id FROM MealPollOptionEntity o
            WHERE o.dailyMenuEntry.dailyMenu.id = :dailyMenuId
            AND o.dailyMenuEntry.id IS NOT NULL
            """)
    Set<UUID> findReferencedEntryIdsByDailyMenuId(@Param("dailyMenuId") UUID dailyMenuId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM MealPollOptionEntity o WHERE o.poll.id = :pollId")
    void deleteByPollId(@Param("pollId") UUID pollId);
}
