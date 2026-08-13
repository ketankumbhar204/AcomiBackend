package com.acomi.acomi_backend.meal.application.service;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.meal.api.dto.request.CreateFoodCategoryRequest;
import com.acomi.acomi_backend.meal.api.dto.request.CreateFoodItemRequest;
import com.acomi.acomi_backend.meal.api.dto.request.UpdateFoodItemDefaultPriceRequest;
import com.acomi.acomi_backend.meal.api.dto.request.UpdateFoodItemExtraRequest;
import com.acomi.acomi_backend.meal.api.dto.request.UpdateFoodItemRequest;
import com.acomi.acomi_backend.meal.api.dto.response.FoodCategoryResponse;
import com.acomi.acomi_backend.meal.api.dto.response.FoodItemResponse;
import com.acomi.acomi_backend.meal.domain.model.FoodScope;
import com.acomi.acomi_backend.meal.domain.model.FoodType;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.FoodCategoryEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.FoodItemEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.SpaceFoodCategorySettingsEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.SpaceFoodItemSettingsEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.FoodCategoryRepository;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.FoodItemRepository;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.SpaceFoodCategorySettingsRepository;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.SpaceFoodItemSettingsRepository;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FoodCatalogService {

    private final FoodCategoryRepository foodCategoryRepository;
    private final FoodItemRepository foodItemRepository;
    private final SpaceFoodItemSettingsRepository spaceFoodItemSettingsRepository;
    private final SpaceFoodCategorySettingsRepository spaceFoodCategorySettingsRepository;
    private final SpaceRepository spaceRepository;
    private final MealAccessService mealAccessService;
    private final MealSpaceSetupService mealSpaceSetupService;

    @Transactional
    public List<FoodCategoryResponse> listCategories(UUID spaceId, UUID callerId) {
        mealAccessService.requireViewMeals(spaceId, callerId);
        SpaceEntity space = loadSpace(spaceId);
        ensureGlobalCatalogPresent();
        mealSpaceSetupService.ensureSampleCombos(space);
        Map<UUID, Long> itemCountsByCategory = foodItemRepository.countVisibleItemsGroupedByCategory(spaceId).stream()
                .collect(Collectors.toMap(
                        FoodItemRepository.CategoryItemCount::getCategoryId,
                        FoodItemRepository.CategoryItemCount::getItemCount));
        return foodCategoryRepository.findVisibleForSpace(spaceId).stream()
                .map(category -> FoodCategoryResponse.from(
                        category, itemCountsByCategory.getOrDefault(category.getId(), 0L)))
                .toList();
    }

    @Transactional
    public FoodCategoryResponse createCategory(UUID spaceId, UUID callerId, CreateFoodCategoryRequest request) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        SpaceEntity space = loadSpace(spaceId);
        FoodCategoryEntity category = foodCategoryRepository.save(FoodCategoryEntity.builder()
                .name(request.getName().trim())
                .sortOrder(request.getSortOrder())
                .scope(FoodScope.SPACE)
                .space(space)
                .isActive(true)
                .build());
        return FoodCategoryResponse.from(category, 0);
    }

    @Transactional(readOnly = true)
    public List<FoodItemResponse> listItems(UUID spaceId, UUID callerId, UUID categoryId) {
        mealAccessService.requireViewMeals(spaceId, callerId);
        ensureGlobalCatalogPresent();
        List<FoodItemEntity> items = categoryId == null
                ? foodItemRepository.findAllVisibleForSpace(spaceId)
                : foodItemRepository.findVisibleForSpaceInCategory(spaceId, categoryId);
        Map<UUID, SpaceFoodItemSettingsEntity> settingsByItemId =
                spaceFoodItemSettingsRepository.findAllBySpaceId(spaceId).stream()
                        .collect(Collectors.toMap(SpaceFoodItemSettingsEntity::getItemId, settings -> settings));
        return items.stream()
                .map(item -> toItemResponse(item, settingsByItemId.get(item.getId())))
                .toList();
    }

    @Transactional
    public FoodItemResponse createItem(UUID spaceId, UUID callerId, CreateFoodItemRequest request) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        SpaceEntity space = loadSpace(spaceId);
        FoodCategoryEntity category = resolveCategoryForSpaceItem(spaceId, request.getCategoryId());
        FoodItemEntity item = foodItemRepository.save(FoodItemEntity.builder()
                .category(category)
                .name(request.getName().trim())
                .scope(FoodScope.SPACE)
                .space(space)
                .isActive(true)
                .isCustom(true)
                .foodType(resolveFoodType(request.getFoodType()))
                .build());
        FoodItemEntity loaded = foodItemRepository
                .findByIdWithCategory(item.getId())
                .orElseThrow(() -> new ResourceNotFoundException("FoodItem", "id", item.getId()));
        SpaceFoodItemSettingsEntity settings = null;
        if (Boolean.TRUE.equals(request.getIsExtra())) {
            if (space.getType() != SpaceType.MESS) {
                throw new BusinessException(
                        "Meal extras are only supported for Mess spaces", HttpStatus.BAD_REQUEST);
            }
            settings = upsertExtraFlag(spaceId, item.getId(), true);
        }
        return toItemResponse(loaded, settings);
    }

    @Transactional
    public FoodItemResponse updateItemExtra(
            UUID spaceId, UUID itemId, UUID callerId, UpdateFoodItemExtraRequest request) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        SpaceEntity space = loadSpace(spaceId);
        if (space.getType() != SpaceType.MESS) {
            throw new BusinessException(
                    "Meal extras are only supported for Mess spaces", HttpStatus.BAD_REQUEST);
        }
        FoodItemEntity item = foodItemRepository
                .findByIdWithCategory(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("FoodItem", "id", itemId));
        ensureItemVisibleForSpace(spaceId, item);
        SpaceFoodItemSettingsEntity settings = upsertExtraFlag(spaceId, itemId, request.isExtra());
        return toItemResponse(item, settings);
    }

    /** True when this catalog item is marked as a Menu Library extra for the space. */
    @Transactional(readOnly = true)
    public boolean isConfiguredExtra(UUID spaceId, UUID itemId) {
        return spaceFoodItemSettingsRepository
                .findBySpaceIdAndItemId(spaceId, itemId)
                .map(SpaceFoodItemSettingsEntity::isExtra)
                .orElse(false);
    }

    @Transactional
    public FoodItemResponse updateItemDefaultPrice(
            UUID spaceId, UUID itemId, UUID callerId, UpdateFoodItemDefaultPriceRequest request) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        FoodItemEntity item = foodItemRepository
                .findByIdWithCategory(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("FoodItem", "id", itemId));
        ensureItemVisibleForSpace(spaceId, item);

        String currencyCode = request.getCurrencyCode() != null && !request.getCurrencyCode().isBlank()
                ? request.getCurrencyCode().trim().toUpperCase()
                : "INR";
        LocalDateTime now = LocalDateTime.now();
        SpaceFoodItemSettingsEntity settings = spaceFoodItemSettingsRepository
                .findBySpaceIdAndItemId(spaceId, itemId)
                .orElse(SpaceFoodItemSettingsEntity.builder()
                        .spaceId(spaceId)
                        .itemId(itemId)
                        .isEnabled(true)
                        .updatedAt(now)
                        .build());
        settings.setDefaultPrice(request.getPrice());
        settings.setCurrencyCode(currencyCode);
        settings.setUpdatedAt(now);
        spaceFoodItemSettingsRepository.save(settings);
        return toItemResponse(item, settings);
    }

    @Transactional
    public FoodItemResponse updateItem(UUID spaceId, UUID itemId, UUID callerId, UpdateFoodItemRequest request) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        FoodItemEntity item = foodItemRepository
                .findSpaceItem(itemId, spaceId)
                .orElseThrow(() -> new BusinessException("Only space custom items can be edited", HttpStatus.FORBIDDEN));
        if (request.getCategoryId() != null) {
            item.setCategory(resolveCategoryForSpaceItem(spaceId, request.getCategoryId()));
        }
        item.setName(request.getName().trim());
        if (request.getFoodType() != null) {
            item.setFoodType(request.getFoodType());
        }
        FoodItemEntity saved = foodItemRepository.save(item);
        SpaceFoodItemSettingsEntity settings = spaceFoodItemSettingsRepository
                .findBySpaceIdAndItemId(spaceId, itemId)
                .orElse(null);
        return toItemResponse(saved, settings);
    }

    @Transactional
    public void deactivateCategory(UUID spaceId, UUID categoryId, UUID callerId) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        FoodCategoryEntity category = foodCategoryRepository
                .findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("FoodCategory", "id", categoryId));

        if (category.getScope() == FoodScope.GLOBAL) {
            SpaceFoodCategorySettingsEntity settings = spaceFoodCategorySettingsRepository
                    .findBySpaceIdAndCategoryId(spaceId, categoryId)
                    .orElse(SpaceFoodCategorySettingsEntity.builder()
                            .spaceId(spaceId)
                            .categoryId(categoryId)
                            .isEnabled(false)
                            .updatedAt(LocalDateTime.now())
                            .build());
            settings.setEnabled(false);
            settings.setUpdatedAt(LocalDateTime.now());
            spaceFoodCategorySettingsRepository.save(settings);
            return;
        }

        if (category.getSpace() == null || !category.getSpace().getId().equals(spaceId)) {
            throw new BusinessException("Category does not belong to this space", HttpStatus.FORBIDDEN);
        }

        category.setActive(false);
        foodCategoryRepository.save(category);
        foodItemRepository.findActiveSpaceItemsInCategory(spaceId, categoryId).forEach(item -> {
            item.setActive(false);
            foodItemRepository.save(item);
        });
    }

    @Transactional
    public void deactivateItem(UUID spaceId, UUID itemId, UUID callerId) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        FoodItemEntity item = foodItemRepository
                .findByIdWithCategory(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("FoodItem", "id", itemId));

        if (item.getScope() == FoodScope.GLOBAL) {
            SpaceFoodItemSettingsEntity settings = spaceFoodItemSettingsRepository
                    .findBySpaceIdAndItemId(spaceId, itemId)
                    .orElse(SpaceFoodItemSettingsEntity.builder()
                            .spaceId(spaceId)
                            .itemId(itemId)
                            .isEnabled(false)
                            .updatedAt(LocalDateTime.now())
                            .build());
            settings.setEnabled(false);
            settings.setUpdatedAt(LocalDateTime.now());
            spaceFoodItemSettingsRepository.save(settings);
            return;
        }

        if (item.getSpace() == null || !item.getSpace().getId().equals(spaceId)) {
            throw new BusinessException("Item does not belong to this space", HttpStatus.FORBIDDEN);
        }
        item.setActive(false);
        foodItemRepository.save(item);
    }

    public FoodItemEntity loadEnabledItemForSpace(UUID spaceId, UUID itemId) {
        FoodItemEntity item = foodItemRepository
                .findByIdWithCategory(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("FoodItem", "id", itemId));
        if (!item.isActive()) {
            throw new BusinessException("Food item is not active");
        }
        if (item.getScope() == FoodScope.SPACE && !item.getSpace().getId().equals(spaceId)) {
            throw new BusinessException("Food item does not belong to this space", HttpStatus.FORBIDDEN);
        }
        if (item.getScope() == FoodScope.GLOBAL) {
            spaceFoodItemSettingsRepository
                    .findBySpaceIdAndItemId(spaceId, itemId)
                    .filter(settings -> !settings.isEnabled())
                    .ifPresent(settings -> {
                        throw new BusinessException("Food item is disabled for this space");
                    });
        }
        return item;
    }

    private void ensureGlobalCatalogPresent() {
        long globalCategoryCount = foodCategoryRepository.countGlobalActive();
        if (globalCategoryCount == 0) {
            log.error(
                    "Global food catalog is empty (expected 12 categories from Flyway V40/V43). "
                            + "Menu library APIs will return empty lists until migrations are applied.");
        }
    }

    private FoodCategoryEntity resolveCategoryForSpaceItem(UUID spaceId, UUID categoryId) {
        return foodCategoryRepository
                .findById(categoryId)
                .filter(category -> category.isActive()
                        && (category.getScope() == FoodScope.GLOBAL
                                || (category.getScope() == FoodScope.SPACE
                                        && category.getSpace() != null
                                        && category.getSpace().getId().equals(spaceId))))
                .orElseThrow(() -> new ResourceNotFoundException("FoodCategory", "id", categoryId));
    }

    private SpaceEntity loadSpace(UUID spaceId) {
        return spaceRepository
                .findById(spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Space", "id", spaceId));
    }

    private FoodType resolveFoodType(FoodType foodType) {
        return foodType != null ? foodType : FoodType.VEG;
    }

    private FoodItemResponse toItemResponse(FoodItemEntity item, SpaceFoodItemSettingsEntity settings) {
        BigDecimal defaultPrice = settings != null ? settings.getDefaultPrice() : null;
        String currencyCode = settings != null ? settings.getCurrencyCode() : null;
        boolean isExtra = settings != null && settings.isExtra();
        return FoodItemResponse.from(item, defaultPrice, currencyCode, isExtra);
    }

    private SpaceFoodItemSettingsEntity upsertExtraFlag(UUID spaceId, UUID itemId, boolean isExtra) {
        LocalDateTime now = LocalDateTime.now();
        SpaceFoodItemSettingsEntity settings = spaceFoodItemSettingsRepository
                .findBySpaceIdAndItemId(spaceId, itemId)
                .orElse(SpaceFoodItemSettingsEntity.builder()
                        .spaceId(spaceId)
                        .itemId(itemId)
                        .isEnabled(true)
                        .updatedAt(now)
                        .build());
        settings.setExtra(isExtra);
        settings.setUpdatedAt(now);
        return spaceFoodItemSettingsRepository.save(settings);
    }

    private void ensureItemVisibleForSpace(UUID spaceId, FoodItemEntity item) {
        if (!item.isActive()) {
            throw new BusinessException("Food item is not active");
        }
        if (item.getScope() == FoodScope.SPACE
                && (item.getSpace() == null || !item.getSpace().getId().equals(spaceId))) {
            throw new BusinessException("Food item does not belong to this space", HttpStatus.FORBIDDEN);
        }
        if (item.getScope() == FoodScope.GLOBAL) {
            spaceFoodItemSettingsRepository
                    .findBySpaceIdAndItemId(spaceId, item.getId())
                    .filter(settings -> !settings.isEnabled())
                    .ifPresent(settings -> {
                        throw new BusinessException("Food item is disabled for this space");
                    });
        }
    }
}
