package com.acomi.acomi_backend.meal.application.service;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.meal.api.dto.request.CreateComboInlineItemRequest;
import com.acomi.acomi_backend.meal.api.dto.request.CreateFoodItemRequest;
import com.acomi.acomi_backend.meal.api.dto.request.CreateMealComboRequest;
import com.acomi.acomi_backend.meal.api.dto.request.MealComboItemQuantityRequest;
import com.acomi.acomi_backend.meal.api.dto.request.UpdateMealComboPriceRequest;
import com.acomi.acomi_backend.meal.api.dto.request.UpdateMealComboRequest;
import com.acomi.acomi_backend.meal.api.dto.response.MealComboResponse;
import com.acomi.acomi_backend.meal.domain.policy.FoodTypeResolver;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealComboEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealComboItemEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.MealComboItemRepository;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.MealComboRepository;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MealComboService {

    private final MealComboRepository mealComboRepository;
    private final MealComboItemRepository mealComboItemRepository;
    private final FoodCatalogService foodCatalogService;
    private final MealSpaceSetupService mealSpaceSetupService;
    private final SpaceRepository spaceRepository;
    private final MealAccessService mealAccessService;

    @Transactional
    public List<MealComboResponse> listCombos(UUID spaceId, UUID callerId) {
        mealAccessService.requireViewMeals(spaceId, callerId);
        SpaceEntity space = loadSpace(spaceId);
        mealSpaceSetupService.ensureSampleCombos(space);
        return mealComboRepository.findBySpaceIdAndIsActiveTrueOrderByNameAsc(spaceId).stream()
                .map(combo -> MealComboResponse.from(combo, mealComboItemRepository.findByComboIdWithItems(combo.getId())))
                .toList();
    }

    @Transactional
    public MealComboResponse createCombo(UUID spaceId, UUID callerId, CreateMealComboRequest request) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        SpaceEntity space = loadSpace(spaceId);
        MealComboEntity combo = mealComboRepository.save(MealComboEntity.builder()
                .space(space)
                .name(request.getName().trim())
                .description(request.getDescription())
                .price(resolvePrice(request.getPrice()))
                .currencyCode(MealPriceValidator.resolveCurrencyCode(request.getCurrencyCode()))
                .isActive(true)
                .build());
        saveComboItems(
                space,
                callerId,
                combo,
                request.getItemIds(),
                request.getNewItems(),
                request.getItemQuantities());
        applyComboFoodType(combo);
        mealComboRepository.save(combo);
        return MealComboResponse.from(combo, mealComboItemRepository.findByComboIdWithItems(combo.getId()));
    }

    @Transactional
    public MealComboResponse updateCombo(UUID spaceId, UUID comboId, UUID callerId, UpdateMealComboRequest request) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        SpaceEntity space = loadSpace(spaceId);
        MealComboEntity combo = loadCombo(spaceId, comboId);
        combo.setName(request.getName().trim());
        combo.setDescription(request.getDescription());
        combo.setPrice(resolvePrice(request.getPrice()));
        combo.setCurrencyCode(MealPriceValidator.resolveCurrencyCode(request.getCurrencyCode()));
        if (request.getActive() != null) {
            combo.setActive(request.getActive());
        }
        mealComboRepository.save(combo);
        mealComboItemRepository.deleteByComboId(comboId);
        saveComboItems(
                space,
                callerId,
                combo,
                request.getItemIds(),
                request.getNewItems(),
                request.getItemQuantities());
        applyComboFoodType(combo);
        mealComboRepository.save(combo);
        return MealComboResponse.from(combo, mealComboItemRepository.findByComboIdWithItems(combo.getId()));
    }

    @Transactional
    public MealComboResponse updateComboPrice(
            UUID spaceId, UUID comboId, UUID callerId, UpdateMealComboPriceRequest request) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        MealComboEntity combo = loadCombo(spaceId, comboId);
        combo.setPrice(resolvePrice(request.getPrice()));
        combo.setCurrencyCode(MealPriceValidator.resolveCurrencyCode(request.getCurrencyCode()));
        mealComboRepository.save(combo);
        return MealComboResponse.from(combo, mealComboItemRepository.findByComboIdWithItems(combo.getId()));
    }

    @Transactional
    public void deactivateCombo(UUID spaceId, UUID comboId, UUID callerId) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        MealComboEntity combo = loadCombo(spaceId, comboId);
        combo.setActive(false);
        mealComboRepository.save(combo);
    }

    public MealComboEntity loadCombo(UUID spaceId, UUID comboId) {
        return mealComboRepository
                .findByIdAndSpaceId(comboId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("MealCombo", "id", comboId));
    }

    private void saveComboItems(
            SpaceEntity space,
            UUID callerId,
            MealComboEntity combo,
            List<UUID> itemIds,
            List<CreateComboInlineItemRequest> newItems,
            List<MealComboItemQuantityRequest> itemQuantities) {
        UUID spaceId = space.getId();
        List<UUID> resolvedItemIds = resolveItemIds(spaceId, callerId, itemIds, newItems);
        Map<UUID, Integer> quantityByItemId = resolveQuantityMap(space, itemQuantities);
        int sortOrder = 0;
        for (UUID itemId : resolvedItemIds) {
            int quantity = quantityByItemId.getOrDefault(itemId, 1);
            mealComboItemRepository.save(MealComboItemEntity.builder()
                    .combo(combo)
                    .item(foodCatalogService.loadEnabledItemForSpace(spaceId, itemId))
                    .sortOrder(sortOrder++)
                    .quantity(quantity)
                    .build());
        }
    }

    private Map<UUID, Integer> resolveQuantityMap(
            SpaceEntity space, List<MealComboItemQuantityRequest> itemQuantities) {
        Map<UUID, Integer> quantityByItemId = new HashMap<>();
        if (space.getType() != SpaceType.MESS || itemQuantities == null) {
            return quantityByItemId;
        }
        for (MealComboItemQuantityRequest row : itemQuantities) {
            if (row == null || row.getItemId() == null) {
                continue;
            }
            int quantity = row.getQuantity() == null ? 1 : row.getQuantity();
            if (quantity < 1) {
                throw new BusinessException("Combo item quantity must be at least 1");
            }
            quantityByItemId.put(row.getItemId(), quantity);
        }
        return quantityByItemId;
    }

    private List<UUID> resolveItemIds(
            UUID spaceId,
            UUID callerId,
            List<UUID> itemIds,
            List<CreateComboInlineItemRequest> newItems) {
        List<UUID> resolved = new ArrayList<>();
        if (itemIds != null) {
            resolved.addAll(itemIds);
        }
        if (newItems != null) {
            for (CreateComboInlineItemRequest newItem : newItems) {
                CreateFoodItemRequest itemRequest = new CreateFoodItemRequest();
                itemRequest.setCategoryId(newItem.getCategoryId());
                itemRequest.setName(newItem.getName());
                itemRequest.setFoodType(newItem.getFoodType());
                resolved.add(foodCatalogService.createItem(spaceId, callerId, itemRequest).getItemId());
            }
        }
        if (resolved.isEmpty()) {
            throw new BusinessException("Combo must include at least one item");
        }
        return new ArrayList<>(new LinkedHashSet<>(resolved));
    }

    private SpaceEntity loadSpace(UUID spaceId) {
        return spaceRepository
                .findById(spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Space", "id", spaceId));
    }

    private BigDecimal resolvePrice(BigDecimal price) {
        MealPriceValidator.validateOptionalPrice(price);
        return price;
    }

    private void applyComboFoodType(MealComboEntity combo) {
        List<MealComboItemEntity> comboItems = mealComboItemRepository.findByComboIdWithItems(combo.getId());
        combo.setFoodType(FoodTypeResolver.resolveStrictestFromItems(
                comboItems.stream().map(MealComboItemEntity::getItem).toList()));
    }
}
