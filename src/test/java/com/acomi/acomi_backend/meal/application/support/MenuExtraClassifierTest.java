package com.acomi.acomi_backend.meal.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.meal.domain.model.DailyMenuEntryType;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.DailyMenuEntryEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.DailyMenuPackageItemEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.FoodItemEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.SpaceFoodItemSettingsEntity;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MenuExtraClassifierTest {

    @Test
    void entryFlagWins() {
        DailyMenuEntryEntity entry = DailyMenuEntryEntity.builder()
                .entryType(DailyMenuEntryType.PACKAGE)
                .isExtra(true)
                .build();

        assertThat(MenuExtraClassifier.isPresentedAsExtra(entry, List.of(), Set.of())).isTrue();
    }

    @Test
    void singlePackageOfCatalogExtraIsPresentedAsExtra() {
        UUID itemId = UUID.randomUUID();
        FoodItemEntity item = FoodItemEntity.builder().name("Papad").build();
        item.setId(itemId);
        DailyMenuEntryEntity entry = DailyMenuEntryEntity.builder()
                .entryType(DailyMenuEntryType.PACKAGE)
                .isExtra(false)
                .build();
        DailyMenuPackageItemEntity line = DailyMenuPackageItemEntity.builder()
                .item(item)
                .sortOrder(1)
                .build();

        assertThat(MenuExtraClassifier.isPresentedAsExtra(entry, List.of(line), Set.of(itemId))).isTrue();
    }

    @Test
    void comboAndUnmarkedPackageStayMainItems() {
        UUID itemId = UUID.randomUUID();
        FoodItemEntity item = FoodItemEntity.builder().name("Rice").build();
        item.setId(itemId);
        DailyMenuEntryEntity combo = DailyMenuEntryEntity.builder()
                .entryType(DailyMenuEntryType.COMBO)
                .isExtra(false)
                .build();
        DailyMenuEntryEntity packageEntry = DailyMenuEntryEntity.builder()
                .entryType(DailyMenuEntryType.PACKAGE)
                .isExtra(false)
                .build();
        DailyMenuPackageItemEntity line = DailyMenuPackageItemEntity.builder()
                .item(item)
                .sortOrder(1)
                .build();

        assertThat(MenuExtraClassifier.isPresentedAsExtra(combo, List.of(), Set.of(itemId))).isFalse();
        assertThat(MenuExtraClassifier.isPresentedAsExtra(packageEntry, List.of(line), Set.of())).isFalse();
    }

    @Test
    void catalogExtraIdsReadsSettings() {
        UUID extraId = UUID.randomUUID();
        UUID mainId = UUID.randomUUID();
        SpaceFoodItemSettingsEntity extra = SpaceFoodItemSettingsEntity.builder()
                .itemId(extraId)
                .isExtra(true)
                .build();
        SpaceFoodItemSettingsEntity main = SpaceFoodItemSettingsEntity.builder()
                .itemId(mainId)
                .isExtra(false)
                .build();

        assertThat(MenuExtraClassifier.catalogExtraIds(List.of(extra, main))).containsExactly(extraId);
    }
}
