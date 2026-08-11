package com.amico.amico_backend.meal.infrastructure.persistence.repository;

import com.amico.amico_backend.meal.infrastructure.persistence.entity.DailyMenuEntryEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DailyMenuEntryRepository extends JpaRepository<DailyMenuEntryEntity, UUID> {

    @Query("""
            SELECT e FROM DailyMenuEntryEntity e
            LEFT JOIN FETCH e.combo
            LEFT JOIN FETCH e.item
            WHERE e.dailyMenu.id = :menuId
            ORDER BY e.sortOrder ASC
            """)
    List<DailyMenuEntryEntity> findByDailyMenuId(@Param("menuId") UUID menuId);

    @Query("""
            SELECT e FROM DailyMenuEntryEntity e
            LEFT JOIN FETCH e.combo
            LEFT JOIN FETCH e.item
            WHERE e.dailyMenu.id IN :menuIds
            ORDER BY e.sortOrder ASC
            """)
    List<DailyMenuEntryEntity> findByDailyMenuIdIn(@Param("menuIds") Collection<UUID> menuIds);

    @Query("""
            SELECT COUNT(e) > 0 FROM DailyMenuEntryEntity e
            WHERE e.dailyMenu.id = :menuId
              AND e.isAvailable = true
            """)
    boolean hasAvailableOptions(@Param("menuId") UUID menuId);

    void deleteByDailyMenuId(UUID dailyMenuId);
}
