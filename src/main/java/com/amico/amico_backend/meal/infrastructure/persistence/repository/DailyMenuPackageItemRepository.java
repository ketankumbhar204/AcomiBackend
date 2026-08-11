package com.amico.amico_backend.meal.infrastructure.persistence.repository;

import com.amico.amico_backend.meal.infrastructure.persistence.entity.DailyMenuPackageItemEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DailyMenuPackageItemRepository extends JpaRepository<DailyMenuPackageItemEntity, UUID> {

    @Query("""
            SELECT pi FROM DailyMenuPackageItemEntity pi
            JOIN FETCH pi.item
            WHERE pi.entry.id = :entryId
            ORDER BY pi.sortOrder ASC
            """)
    List<DailyMenuPackageItemEntity> findByEntryIdWithItems(@Param("entryId") UUID entryId);

    @Query("""
            SELECT pi FROM DailyMenuPackageItemEntity pi
            JOIN FETCH pi.item
            JOIN FETCH pi.entry
            WHERE pi.entry.id IN :entryIds
            ORDER BY pi.sortOrder ASC
            """)
    List<DailyMenuPackageItemEntity> findByEntryIdInWithItems(@Param("entryIds") Collection<UUID> entryIds);

    void deleteByEntryId(UUID entryId);
}
