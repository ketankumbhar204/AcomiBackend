package com.amico.amico_backend.meal.application.service;

import com.amico.amico_backend.meal.domain.model.FoodType;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealComboEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealComboItemEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.repository.FoodItemRepository;
import com.amico.amico_backend.meal.infrastructure.persistence.repository.MealComboItemRepository;
import com.amico.amico_backend.meal.infrastructure.persistence.repository.MealComboRepository;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MealSpaceSetupService {

    private static final UUID CHAPATI_ID = UUID.fromString("22222222-2222-2222-2222-222222220001");
    private static final UUID DAL_FRY_ID = UUID.fromString("22222222-2222-2222-2222-222222220020");
    private static final UUID PLAIN_RICE_ID = UUID.fromString("22222222-2222-2222-2222-222222220010");
    private static final UUID GREEN_SALAD_ID = UUID.fromString("22222222-2222-2222-2222-222222220100");
    private static final UUID CHICKEN_CURRY_ID = UUID.fromString("22222222-2222-2222-2222-222222220113");
    private static final UUID EGG_BHURJI_ID = UUID.fromString("22222222-2222-2222-2222-222222220118");

    private final MealComboRepository mealComboRepository;
    private final MealComboItemRepository mealComboItemRepository;
    private final FoodItemRepository foodItemRepository;

    @Transactional
    public void ensureSampleCombos(SpaceEntity space) {
        if (!space.isActive()) {
            return;
        }
        if (foodItemRepository.findGlobalByName("Chapati").isEmpty()) {
            log.warn(
                    "Cannot seed sample combos for space {} — global food catalog is missing. "
                            + "Apply Flyway V40 or V43.",
                    space.getId());
            return;
        }
        ensureCombo(
                space,
                "Standard Lunch Thali",
                "Daily lunch combo",
                FoodType.VEG,
                List.of(CHAPATI_ID, DAL_FRY_ID, PLAIN_RICE_ID, GREEN_SALAD_ID));
        ensureCombo(
                space,
                "Dal Rice Combo",
                "Simple dal rice",
                FoodType.VEG,
                List.of(DAL_FRY_ID, PLAIN_RICE_ID));
        ensureCombo(
                space,
                "Chicken Thali",
                "Non-veg lunch combo",
                FoodType.NON_VEG,
                List.of(CHAPATI_ID, CHICKEN_CURRY_ID, PLAIN_RICE_ID));
        ensureCombo(
                space,
                "Egg Combo",
                "Egg meal combo",
                FoodType.EGG,
                List.of(CHAPATI_ID, EGG_BHURJI_ID, PLAIN_RICE_ID));
    }

    private void ensureCombo(
            SpaceEntity space,
            String name,
            String description,
            FoodType foodType,
            List<UUID> itemIds) {
        if (mealComboRepository.existsBySpaceIdAndNameIgnoreCaseAndIsActiveTrue(space.getId(), name)) {
            return;
        }
        log.info("Seeding meal combo '{}' for space {} ({})", name, space.getId(), space.getType());
        createCombo(space, name, description, foodType, itemIds);
    }

    private void createCombo(
            SpaceEntity space, String name, String description, FoodType foodType, List<UUID> itemIds) {
        MealComboEntity combo = mealComboRepository.save(MealComboEntity.builder()
                .space(space)
                .name(name)
                .description(description)
                .foodType(foodType)
                .isActive(true)
                .build());
        for (int index = 0; index < itemIds.size(); index++) {
            UUID itemId = itemIds.get(index);
            int sortOrder = index;
            foodItemRepository.findByIdWithCategory(itemId).ifPresent(item -> mealComboItemRepository.save(
                    MealComboItemEntity.builder()
                            .combo(combo)
                            .item(item)
                            .sortOrder(sortOrder)
                            .build()));
        }
    }
}
