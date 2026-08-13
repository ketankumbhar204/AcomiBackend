package com.acomi.acomi_backend.meal.infrastructure.persistence.repository;

import com.acomi.acomi_backend.meal.domain.model.MealType;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MenuPlanningHistoryEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MenuPlanningHistoryRepository extends JpaRepository<MenuPlanningHistoryEntity, UUID> {

    /**
     * Active history for one space + meal type.
     * Excludes catalog extras (those belong in the Extras section, not History).
     */
    @Query(
            """
            SELECT h FROM MenuPlanningHistoryEntity h
            WHERE h.space.id = :spaceId
              AND h.mealType = :mealType
              AND h.deleted = false
              AND (
                    h.entryType = com.acomi.acomi_backend.meal.domain.model.MenuHistoryEntryType.COMBO
                    OR h.item IS NULL
                    OR NOT EXISTS (
                        SELECT 1 FROM com.acomi.acomi_backend.meal.infrastructure.persistence.entity.SpaceFoodItemSettingsEntity s
                        WHERE s.spaceId = :spaceId
                          AND s.itemId = h.item.id
                          AND s.isExtra = true
                    )
              )
              AND (
                    :search IS NULL OR :search = ''
                    OR LOWER(h.label) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(h.summary, '')) LIKE LOWER(CONCAT('%', :search, '%'))
              )
            """)
    Page<MenuPlanningHistoryEntity> findActiveForMealPaged(
            @Param("spaceId") UUID spaceId,
            @Param("mealType") MealType mealType,
            @Param("search") String search,
            Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            """
            DELETE FROM MenuPlanningHistoryEntity h
            WHERE h.space.id = :spaceId
              AND h.mealType = :mealType
            """)
    int deleteAllForMeal(@Param("spaceId") UUID spaceId, @Param("mealType") MealType mealType);

    Optional<MenuPlanningHistoryEntity> findFirstBySpace_IdAndMealTypeAndCombo_IdAndDeletedFalse(
            UUID spaceId, MealType mealType, UUID comboId);

    Optional<MenuPlanningHistoryEntity> findFirstBySpace_IdAndMealTypeAndItem_IdAndDeletedFalse(
            UUID spaceId, MealType mealType, UUID itemId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            """
            UPDATE MenuPlanningHistoryEntity h
            SET h.deleted = true
            WHERE h.space.id = :spaceId
              AND h.mealType = :mealType
              AND h.deleted = false
            """)
    int softDeleteForMeal(@Param("spaceId") UUID spaceId, @Param("mealType") MealType mealType);

    /**
     * True when {@code mealType} is not a primary usage meal for this combo
     * (another meal type has a strictly higher usage count).
     */
    @Query(
            value =
                    """
                    SELECT NOT EXISTS (
                        SELECT 1 FROM (
                            SELECT dm.meal_type AS mt,
                                   RANK() OVER (ORDER BY COUNT(*) DESC) AS rnk
                            FROM daily_menu_entries dme
                            JOIN daily_menus dm ON dm.id = dme.daily_menu_id
                            WHERE dm.space_id = :spaceId
                              AND dm.is_deleted = FALSE
                              AND dme.is_available = TRUE
                              AND COALESCE(dme.is_extra, FALSE) = FALSE
                              AND dme.entry_type = 'COMBO'
                              AND dme.combo_id = :comboId
                            GROUP BY dm.meal_type
                        ) ranked
                        WHERE ranked.mt = :mealType AND ranked.rnk = 1
                    )
                    """,
            nativeQuery = true)
    boolean comboPrimaryIsOtherMeal(
            @Param("spaceId") UUID spaceId,
            @Param("mealType") String mealType,
            @Param("comboId") UUID comboId);

    @Query(
            value =
                    """
                    SELECT NOT EXISTS (
                        SELECT 1 FROM (
                            SELECT dm.meal_type AS mt,
                                   RANK() OVER (ORDER BY COUNT(*) DESC) AS rnk
                            FROM daily_menu_entries dme
                            JOIN daily_menus dm ON dm.id = dme.daily_menu_id
                            LEFT JOIN daily_menu_package_items dpi ON dpi.entry_id = dme.id
                            WHERE dm.space_id = :spaceId
                              AND dm.is_deleted = FALSE
                              AND dme.is_available = TRUE
                              AND COALESCE(dme.is_extra, FALSE) = FALSE
                              AND (
                                    (dme.entry_type = 'ITEM' AND dme.item_id = :itemId)
                                    OR (
                                      dme.entry_type = 'PACKAGE'
                                      AND dpi.item_id = :itemId
                                      AND (
                                        SELECT COUNT(*) FROM daily_menu_package_items x
                                        WHERE x.entry_id = dme.id
                                      ) = 1
                                    )
                              )
                            GROUP BY dm.meal_type
                        ) ranked
                        WHERE ranked.mt = :mealType AND ranked.rnk = 1
                    )
                    """,
            nativeQuery = true)
    boolean itemPrimaryIsOtherMeal(
            @Param("spaceId") UUID spaceId,
            @Param("mealType") String mealType,
            @Param("itemId") UUID itemId);
}
