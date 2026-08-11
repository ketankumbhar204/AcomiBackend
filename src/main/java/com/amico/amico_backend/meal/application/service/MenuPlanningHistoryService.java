package com.amico.amico_backend.meal.application.service;

import com.amico.amico_backend.common.exception.BusinessException;
import com.amico.amico_backend.meal.api.dto.response.MenuHistoryItemResponse;
import com.amico.amico_backend.meal.api.dto.response.MenuHistoryPageResponse;
import com.amico.amico_backend.meal.domain.model.DailyMenuEntryType;
import com.amico.amico_backend.meal.domain.model.FoodType;
import com.amico.amico_backend.meal.domain.model.MealType;
import com.amico.amico_backend.meal.domain.model.MenuHistoryEntryType;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.DailyMenuEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.DailyMenuEntryEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.DailyMenuPackageItemEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.FoodItemEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealComboEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealComboItemEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.MenuPlanningHistoryEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.repository.DailyMenuEntryRepository;
import com.amico.amico_backend.meal.infrastructure.persistence.repository.DailyMenuPackageItemRepository;
import com.amico.amico_backend.meal.infrastructure.persistence.repository.DailyMenuRepository;
import com.amico.amico_backend.meal.infrastructure.persistence.repository.MealComboItemRepository;
import com.amico.amico_backend.meal.infrastructure.persistence.repository.MenuPlanningHistoryRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MenuPlanningHistoryService {

    private final MenuPlanningHistoryRepository historyRepository;
    private final DailyMenuRepository dailyMenuRepository;
    private final DailyMenuEntryRepository dailyMenuEntryRepository;
    private final DailyMenuPackageItemRepository packageItemRepository;
    private final MealComboItemRepository mealComboItemRepository;
    private final MealAccessService mealAccessService;
    private final FoodCatalogService foodCatalogService;

    @Transactional(readOnly = true)
    public MenuHistoryPageResponse listHistory(
            UUID spaceId, UUID callerId, MealType mealType, String search, int page, int limit) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        requireMealType(mealType);
        int safePage = Math.max(page, 0);
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        String q = search == null ? "" : search.trim();
        Page<MenuPlanningHistoryEntity> result = historyRepository.findActiveForMealPaged(
                spaceId,
                mealType,
                q.isEmpty() ? null : q,
                PageRequest.of(safePage, safeLimit, Sort.by(Sort.Direction.DESC, "lastUsedAt")));

        // Defensive: never leak another meal type even if data were corrupted.
        List<MenuHistoryItemResponse> items = result.getContent().stream()
                .filter(entity -> entity.getMealType() == mealType)
                .filter(entity -> entity.getSpace() != null && spaceId.equals(entity.getSpace().getId()))
                .map(this::toResponse)
                .filter(item -> item.getMealType() == mealType)
                .collect(Collectors.toList());

        return MenuHistoryPageResponse.builder()
                .items(items)
                .page(safePage)
                .limit(safeLimit)
                .total(result.getTotalElements())
                .hasMore(result.hasNext())
                .build();
    }

    @Transactional
    public void clearHistory(UUID spaceId, UUID callerId, MealType mealType) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        requireMealType(mealType);
        historyRepository.softDeleteForMeal(spaceId, mealType);
    }

    /**
     * Rebuild meal-specific history from daily menus of that meal type only.
     * Deletes existing rows for the meal, then replays menus oldest → newest.
     */
    @Transactional
    public void rebuildForMeal(UUID spaceId, UUID callerId, MealType mealType) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        requireMealType(mealType);
        if (spaceId == null) {
            return;
        }
        // Hard-delete so unique (space, meal, combo/item) indexes allow a clean rebuild.
        historyRepository.deleteAllForMeal(spaceId, mealType);
        List<DailyMenuEntity> menus = dailyMenuRepository.findBySpaceAndMealType(spaceId, mealType).stream()
                .sorted(Comparator.comparing(DailyMenuEntity::getMenuDate)
                        .thenComparing(m -> m.getUpdatedAt() != null ? m.getUpdatedAt() : LocalDateTime.MIN))
                .toList();
        for (DailyMenuEntity menu : menus) {
            if (menu.getMealType() != mealType) {
                continue;
            }
            recordFromMenu(menu);
        }
    }

    /**
     * Upserts history rows for non-extra COMBO and single-item PACKAGE/ITEM entries after menu
     * save/share. Rows are always keyed by {@code spaceId + mealType + combo/item}.
     */
    @Transactional
    public void recordFromMenu(DailyMenuEntity menu) {
        if (menu == null || menu.getSpace() == null) {
            return;
        }
        MealType mealType = menu.getMealType();
        requireMealType(mealType);
        List<DailyMenuEntryEntity> entries = dailyMenuEntryRepository.findByDailyMenuId(menu.getId());
        LocalDateTime usedAt = LocalDateTime.now();
        for (DailyMenuEntryEntity entry : entries) {
            if (!entry.isAvailable() || entry.isExtra()) {
                continue;
            }
            if (entry.getEntryType() == DailyMenuEntryType.COMBO && entry.getCombo() != null) {
                if (historyRepository.comboPrimaryIsOtherMeal(
                        menu.getSpace().getId(), mealType.name(), entry.getCombo().getId())) {
                    // Primary usage is Lunch/Dinner — keep it out of Breakfast history (and vice versa).
                    softDeleteComboHistory(menu.getSpace().getId(), mealType, entry.getCombo().getId());
                    continue;
                }
                recordCombo(menu, entry, usedAt);
            } else if (entry.getEntryType() == DailyMenuEntryType.ITEM && entry.getItem() != null) {
                if (foodCatalogService.isConfiguredExtra(menu.getSpace().getId(), entry.getItem().getId())) {
                    softDeleteItemHistory(menu.getSpace().getId(), mealType, entry.getItem().getId());
                    continue;
                }
                if (historyRepository.itemPrimaryIsOtherMeal(
                        menu.getSpace().getId(), mealType.name(), entry.getItem().getId())) {
                    softDeleteItemHistory(menu.getSpace().getId(), mealType, entry.getItem().getId());
                    continue;
                }
                recordItem(menu, entry, entry.getItem(), usedAt);
            } else if (entry.getEntryType() == DailyMenuEntryType.PACKAGE) {
                List<DailyMenuPackageItemEntity> packageItems =
                        packageItemRepository.findByEntryIdWithItems(entry.getId());
                if (packageItems.size() == 1 && packageItems.get(0).getItem() != null) {
                    FoodItemEntity item = packageItems.get(0).getItem();
                    if (foodCatalogService.isConfiguredExtra(menu.getSpace().getId(), item.getId())) {
                        softDeleteItemHistory(menu.getSpace().getId(), mealType, item.getId());
                        continue;
                    }
                    if (historyRepository.itemPrimaryIsOtherMeal(
                            menu.getSpace().getId(), mealType.name(), item.getId())) {
                        softDeleteItemHistory(menu.getSpace().getId(), mealType, item.getId());
                        continue;
                    }
                    recordItem(menu, entry, item, usedAt);
                }
            }
        }
    }

    private void recordCombo(DailyMenuEntity menu, DailyMenuEntryEntity entry, LocalDateTime usedAt) {
        MealComboEntity combo = entry.getCombo();
        UUID spaceId = menu.getSpace().getId();
        MealType mealType = menu.getMealType();
        MenuPlanningHistoryEntity row = historyRepository
                .findFirstBySpace_IdAndMealTypeAndCombo_IdAndDeletedFalse(spaceId, mealType, combo.getId())
                .orElseGet(() -> MenuPlanningHistoryEntity.builder()
                        .space(menu.getSpace())
                        .mealType(mealType)
                        .entryType(MenuHistoryEntryType.COMBO)
                        .combo(combo)
                        .usageCount(0)
                        .lastUsedAt(usedAt)
                        .build());

        List<MealComboItemEntity> comboItems = mealComboItemRepository.findByComboIdWithItems(combo.getId());
        String summary = comboItems.stream()
                .map(ci -> ci.getItem() != null ? ci.getItem().getName() : null)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.joining(" · "));

        row.setSpace(menu.getSpace());
        row.setMealType(mealType);
        row.setEntryType(MenuHistoryEntryType.COMBO);
        row.setCombo(combo);
        row.setItem(null);
        row.setDeleted(false);
        row.setLabel(firstNonBlank(entry.getLabel(), combo.getName(), "Combo"));
        row.setSummary(summary.isBlank() ? null : truncate(summary, 500));
        row.setFoodType(combo.getFoodType() != null ? combo.getFoodType() : FoodType.VEG);
        row.setPrice(entry.getPrice() != null ? entry.getPrice() : combo.getPrice());
        row.setCurrencyCode(firstNonBlank(entry.getCurrencyCode(), combo.getCurrencyCode(), "INR"));
        row.setUsageCount(row.getUsageCount() + 1);
        row.setLastUsedAt(usedAt);
        row.setLastUsedMenuDate(menu.getMenuDate());
        historyRepository.save(row);
    }

    private void recordItem(
            DailyMenuEntity menu, DailyMenuEntryEntity entry, FoodItemEntity item, LocalDateTime usedAt) {
        UUID spaceId = menu.getSpace().getId();
        MealType mealType = menu.getMealType();
        MenuPlanningHistoryEntity row = historyRepository
                .findFirstBySpace_IdAndMealTypeAndItem_IdAndDeletedFalse(spaceId, mealType, item.getId())
                .orElseGet(() -> MenuPlanningHistoryEntity.builder()
                        .space(menu.getSpace())
                        .mealType(mealType)
                        .entryType(MenuHistoryEntryType.ITEM)
                        .item(item)
                        .usageCount(0)
                        .lastUsedAt(usedAt)
                        .build());

        String categoryName =
                item.getCategory() != null ? item.getCategory().getName() : null;

        row.setSpace(menu.getSpace());
        row.setMealType(mealType);
        row.setEntryType(MenuHistoryEntryType.ITEM);
        row.setCombo(null);
        row.setItem(item);
        row.setDeleted(false);
        row.setLabel(firstNonBlank(entry.getLabel(), item.getName(), "Item"));
        row.setSummary(categoryName);
        row.setFoodType(item.getFoodType() != null ? item.getFoodType() : FoodType.VEG);
        row.setPrice(entry.getPrice());
        row.setCurrencyCode(firstNonBlank(entry.getCurrencyCode(), "INR"));
        row.setUsageCount(row.getUsageCount() + 1);
        row.setLastUsedAt(usedAt);
        row.setLastUsedMenuDate(menu.getMenuDate());
        historyRepository.save(row);
    }

    private void softDeleteComboHistory(UUID spaceId, MealType mealType, UUID comboId) {
        historyRepository
                .findFirstBySpace_IdAndMealTypeAndCombo_IdAndDeletedFalse(spaceId, mealType, comboId)
                .ifPresent(row -> {
                    row.setDeleted(true);
                    historyRepository.save(row);
                });
    }

    private void softDeleteItemHistory(UUID spaceId, MealType mealType, UUID itemId) {
        historyRepository
                .findFirstBySpace_IdAndMealTypeAndItem_IdAndDeletedFalse(spaceId, mealType, itemId)
                .ifPresent(row -> {
                    row.setDeleted(true);
                    historyRepository.save(row);
                });
    }

    private MenuHistoryItemResponse toResponse(MenuPlanningHistoryEntity entity) {
        List<UUID> itemIds = new ArrayList<>();
        String summary = entity.getSummary();
        BigDecimal price = entity.getPrice();
        String currency = entity.getCurrencyCode() != null ? entity.getCurrencyCode() : "INR";
        FoodType foodType = entity.getFoodType() != null ? entity.getFoodType() : FoodType.VEG;
        UUID comboId = entity.getCombo() != null ? entity.getCombo().getId() : null;
        UUID itemId = entity.getItem() != null ? entity.getItem().getId() : null;

        if (entity.getEntryType() == MenuHistoryEntryType.COMBO && comboId != null) {
            List<MealComboItemEntity> comboItems = mealComboItemRepository.findByComboIdWithItems(comboId);
            itemIds = comboItems.stream()
                    .map(ci -> ci.getItem() != null ? ci.getItem().getId() : null)
                    .filter(id -> id != null)
                    .collect(Collectors.toList());
            if (summary == null || summary.isBlank()) {
                summary = comboItems.stream()
                        .map(ci -> ci.getItem() != null ? ci.getItem().getName() : null)
                        .filter(name -> name != null && !name.isBlank())
                        .collect(Collectors.joining(" · "));
            }
            if (price == null && entity.getCombo() != null) {
                price = entity.getCombo().getPrice();
            }
            if (entity.getCombo() != null && entity.getCombo().getFoodType() != null) {
                foodType = entity.getCombo().getFoodType();
            }
        } else if (entity.getEntryType() == MenuHistoryEntryType.ITEM && itemId != null) {
            itemIds = List.of(itemId);
            if ((summary == null || summary.isBlank())
                    && entity.getItem() != null
                    && entity.getItem().getCategory() != null) {
                summary = entity.getItem().getCategory().getName();
            }
            if (entity.getItem() != null && entity.getItem().getFoodType() != null) {
                foodType = entity.getItem().getFoodType();
            }
        }

        return MenuHistoryItemResponse.builder()
                .historyId(entity.getId())
                .type(entity.getEntryType())
                .mealType(entity.getMealType())
                .name(entity.getLabel())
                .thumbnailUrl(null)
                .foodType(foodType)
                .summary(summary)
                .lastUsedAt(entity.getLastUsedAt())
                .lastUsedMenuDate(entity.getLastUsedMenuDate())
                .usageCount(entity.getUsageCount())
                .price(price)
                .currencyCode(currency)
                .comboId(comboId)
                .itemId(itemId)
                .itemIds(itemIds)
                .build();
    }

    private static void requireMealType(MealType mealType) {
        if (mealType == null) {
            throw new BusinessException("mealType is required for menu history", HttpStatus.BAD_REQUEST);
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
