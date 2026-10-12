package com.acomi.acomi_backend.meal.application.support;

import com.acomi.acomi_backend.meal.domain.model.DailyMenuEntryType;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.DailyMenuEntryEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.DailyMenuPackageItemEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.SpaceFoodItemSettingsEntity;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Decides whether a menu line is a Mess extra.
 * The entry flag is the source of truth. A single-item package whose catalog item is an
 * extra is also treated as an extra so meals saved before that flag was persisted still
 * show in the extras section.
 */
public final class MenuExtraClassifier {

    private MenuExtraClassifier() {}

    public static Set<UUID> catalogExtraIds(List<SpaceFoodItemSettingsEntity> settings) {
        if (settings == null || settings.isEmpty()) {
            return Set.of();
        }
        return settings.stream()
                .filter(SpaceFoodItemSettingsEntity::isExtra)
                .map(SpaceFoodItemSettingsEntity::getItemId)
                .collect(Collectors.toSet());
    }

    public static boolean isPresentedAsExtra(
            DailyMenuEntryEntity entry,
            List<DailyMenuPackageItemEntity> packageItems,
            Set<UUID> catalogExtraItemIds) {
        if (entry == null) {
            return false;
        }
        if (entry.isExtra()) {
            return true;
        }
        if (entry.getEntryType() != DailyMenuEntryType.PACKAGE
                || packageItems == null
                || packageItems.size() != 1
                || catalogExtraItemIds == null
                || catalogExtraItemIds.isEmpty()) {
            return false;
        }
        if (packageItems.get(0).getItem() == null) {
            return false;
        }
        return catalogExtraItemIds.contains(packageItems.get(0).getItem().getId());
    }
}
