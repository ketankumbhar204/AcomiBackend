package com.countin.countin_backend.meal.application.service;

import com.countin.countin_backend.common.exception.BusinessException;
import com.countin.countin_backend.common.exception.ResourceNotFoundException;
import com.countin.countin_backend.meal.api.dto.request.CopyDailyMenuRequest;
import com.countin.countin_backend.meal.api.dto.request.DailyMenuOptionRequest;
import com.countin.countin_backend.meal.api.dto.request.UpsertDailyMenuRequest;
import com.countin.countin_backend.meal.api.dto.response.DailyMenuResponse;
import com.countin.countin_backend.meal.domain.model.DailyMenuEntryType;
import com.countin.countin_backend.meal.domain.model.DailyMenuStatus;
import com.countin.countin_backend.meal.domain.model.MealType;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.DailyMenuEntity;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.DailyMenuEntryEntity;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.FoodItemEntity;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.MealComboEntity;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.DailyMenuPackageItemEntity;
import com.countin.countin_backend.meal.infrastructure.persistence.repository.DailyMenuEntryRepository;
import com.countin.countin_backend.meal.infrastructure.persistence.repository.DailyMenuPackageItemRepository;
import com.countin.countin_backend.meal.infrastructure.persistence.repository.DailyMenuRepository;
import com.countin.countin_backend.meal.infrastructure.persistence.repository.MealPollOptionRepository;
import com.countin.countin_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.countin.countin_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.countin.countin_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DailyMenuService {

    private static final int MAX_DATE_RANGE_DAYS = 31;

    private final DailyMenuRepository dailyMenuRepository;
    private final DailyMenuEntryRepository dailyMenuEntryRepository;
    private final DailyMenuPackageItemRepository dailyMenuPackageItemRepository;
    private final MealPollOptionRepository mealPollOptionRepository;
    private final MealComboService mealComboService;
    private final FoodCatalogService foodCatalogService;
    private final SpaceRepository spaceRepository;
    private final MealAccessService mealAccessService;

    @Transactional(readOnly = true)
    public List<DailyMenuResponse> listMenus(UUID spaceId, UUID callerId, LocalDate from, LocalDate to) {
        SpaceMembershipEntity membership = mealAccessService.requireViewMeals(spaceId, callerId);
        validateDateRange(from, to);
        boolean publishedOnly = !mealAccessService.canManageMeals(membership);
        return toResponses(dailyMenuRepository.findBySpaceAndDateRange(spaceId, from, to, publishedOnly));
    }

    @Transactional(readOnly = true)
    public List<DailyMenuResponse> getTodayMenus(UUID spaceId, UUID callerId) {
        mealAccessService.requireViewMeals(spaceId, callerId);
        return toResponses(dailyMenuRepository.findBySpaceAndDate(
                spaceId, LocalDate.now(), true, DailyMenuStatus.PUBLISHED));
    }

    @Transactional(readOnly = true)
    public List<DailyMenuResponse> getMenusByDate(UUID spaceId, UUID callerId, LocalDate date) {
        SpaceMembershipEntity membership = mealAccessService.requireViewMeals(spaceId, callerId);
        boolean publishedOnly = !mealAccessService.canManageMeals(membership);
        return toResponses(dailyMenuRepository.findBySpaceAndDate(
                spaceId, date, publishedOnly, DailyMenuStatus.PUBLISHED));
    }

    @Transactional(readOnly = true)
    public DailyMenuResponse getMenu(UUID spaceId, UUID callerId, LocalDate date, MealType mealType) {
        SpaceMembershipEntity membership = mealAccessService.requireViewMeals(spaceId, callerId);
        DailyMenuEntity menu = loadMenu(spaceId, date, mealType);
        if (!mealAccessService.canManageMeals(membership)
                && menu.getStatus() != DailyMenuStatus.PUBLISHED) {
            throw new BusinessException("Daily menu is not published yet", HttpStatus.NOT_FOUND);
        }
        return toResponse(menu);
    }

    /**
     * Upserts a daily menu. New menus start as DRAFT. Published menus can be edited in-place without
     * reverting to draft (MVP published-edit policy).
     */
    @Transactional
    public DailyMenuResponse upsertMenu(
            UUID spaceId, UUID callerId, LocalDate date, MealType mealType, UpsertDailyMenuRequest request) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        SpaceEntity space = loadSpace(spaceId);
        DailyMenuEntity menu = dailyMenuRepository
                .findBySpaceDateAndType(spaceId, date, mealType)
                .orElseGet(() -> DailyMenuEntity.builder()
                        .space(space)
                        .menuDate(date)
                        .mealType(mealType)
                        .status(DailyMenuStatus.DRAFT)
                        .isDeleted(false)
                        .build());

        if (menu.isDeleted()) {
            menu.setDeleted(false);
            menu.setStatus(DailyMenuStatus.DRAFT);
            menu.setPublishedAt(null);
        }

        menu.setNotes(request.getNotes());
        menu = dailyMenuRepository.save(menu);
        List<DailyMenuOptionRequest> options =
                request.getOptions() != null ? request.getOptions() : Collections.emptyList();
        syncEntries(spaceId, menu, options);
        return toResponse(menu);
    }

    @Transactional
    public DailyMenuResponse publishMenu(UUID spaceId, UUID callerId, LocalDate date, MealType mealType) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        DailyMenuEntity menu = loadMenu(spaceId, date, mealType);
        if (menu.getStatus() == DailyMenuStatus.PUBLISHED) {
            return toResponse(menu);
        }
        List<DailyMenuEntryEntity> entries = dailyMenuEntryRepository.findByDailyMenuId(menu.getId());
        if (entries.stream().noneMatch(DailyMenuEntryEntity::isAvailable)) {
            throw new BusinessException(
                    "At least one available option is required to publish", HttpStatus.BAD_REQUEST);
        }
        menu.setStatus(DailyMenuStatus.PUBLISHED);
        menu.setPublishedAt(LocalDateTime.now());
        return toResponse(dailyMenuRepository.save(menu));
    }

    @Transactional
    public void deleteDraftMenu(UUID spaceId, UUID callerId, LocalDate date, MealType mealType) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        DailyMenuEntity menu = loadMenu(spaceId, date, mealType);
        if (menu.getStatus() == DailyMenuStatus.PUBLISHED) {
            throw new BusinessException("Published menus cannot be deleted", HttpStatus.CONFLICT);
        }
        menu.setDeleted(true);
        dailyMenuRepository.save(menu);
    }

    @Transactional
    public DailyMenuResponse copyMenu(
            UUID spaceId,
            UUID callerId,
            LocalDate targetDate,
            MealType mealType,
            LocalDate sourceDate,
            CopyDailyMenuRequest request) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        DailyMenuEntity source = dailyMenuRepository
                .findBySpaceDateAndType(spaceId, sourceDate, mealType)
                .filter(menu -> !menu.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "DailyMenu", "date/mealType", sourceDate + "/" + mealType));

        boolean force = request != null && request.isForce();
        DailyMenuEntity target = dailyMenuRepository
                .findBySpaceDateAndType(spaceId, targetDate, mealType)
                .filter(menu -> !menu.isDeleted())
                .orElse(null);

        if (target != null && target.getStatus() == DailyMenuStatus.PUBLISHED && !force) {
            throw new BusinessException(
                    "Target menu is published; set force=true to overwrite", HttpStatus.CONFLICT);
        }

        SpaceEntity space = loadSpace(spaceId);
        if (target == null) {
            target = DailyMenuEntity.builder()
                    .space(space)
                    .menuDate(targetDate)
                    .mealType(mealType)
                    .isDeleted(false)
                    .build();
        }

        target.setStatus(DailyMenuStatus.DRAFT);
        target.setPublishedAt(null);
        target.setNotes(source.getNotes());
        target = dailyMenuRepository.save(target);

        removeUnreferencedEntries(target.getId());
        for (DailyMenuEntryEntity sourceEntry :
                dailyMenuEntryRepository.findByDailyMenuId(source.getId())) {
            DailyMenuEntryEntity copied = dailyMenuEntryRepository.save(DailyMenuEntryEntity.builder()
                    .dailyMenu(target)
                    .entryType(sourceEntry.getEntryType())
                    .combo(sourceEntry.getCombo())
                    .item(sourceEntry.getItem())
                    .label(sourceEntry.getLabel())
                    .sortOrder(sourceEntry.getSortOrder())
                    .isAvailable(sourceEntry.isAvailable())
                    .price(sourceEntry.getPrice())
                    .currencyCode(sourceEntry.getCurrencyCode())
                    .build());
            if (sourceEntry.getEntryType() == DailyMenuEntryType.PACKAGE) {
                copyPackageItems(sourceEntry.getId(), copied);
            }
        }

        return toResponse(target);
    }

    public boolean isPublished(UUID spaceId, LocalDate date, MealType mealType) {
        return publishedMealTypes(spaceId, date).contains(mealType);
    }

    /** One query for all published meal types on a date (avoids 3× findBySpaceDateAndType). */
    public Set<MealType> publishedMealTypes(UUID spaceId, LocalDate date) {
        return dailyMenuRepository
                .findBySpaceAndDate(spaceId, date, true, DailyMenuStatus.PUBLISHED)
                .stream()
                .map(DailyMenuEntity::getMealType)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(MealType.class)));
    }

    private List<DailyMenuResponse> toResponses(List<DailyMenuEntity> menus) {
        if (menus.isEmpty()) {
            return List.of();
        }

        List<UUID> menuIds = menus.stream().map(DailyMenuEntity::getId).toList();
        List<DailyMenuEntryEntity> allEntries = dailyMenuEntryRepository.findByDailyMenuIdIn(menuIds);

        Map<UUID, List<DailyMenuEntryEntity>> entriesByMenuId = new HashMap<>();
        for (DailyMenuEntryEntity entry : allEntries) {
            if (entry.getDailyMenu() == null || entry.getDailyMenu().getId() == null) {
                continue;
            }
            entriesByMenuId
                    .computeIfAbsent(entry.getDailyMenu().getId(), ignored -> new ArrayList<>())
                    .add(entry);
        }

        List<UUID> packageEntryIds = allEntries.stream()
                .filter(entry -> entry.getEntryType() == DailyMenuEntryType.PACKAGE)
                .map(DailyMenuEntryEntity::getId)
                .toList();

        Map<UUID, List<DailyMenuPackageItemEntity>> packageItemsByEntryId = new HashMap<>();
        if (!packageEntryIds.isEmpty()) {
            for (DailyMenuPackageItemEntity packageItem :
                    dailyMenuPackageItemRepository.findByEntryIdInWithItems(packageEntryIds)) {
                packageItemsByEntryId
                        .computeIfAbsent(packageItem.getEntry().getId(), ignored -> new ArrayList<>())
                        .add(packageItem);
            }
        }

        List<DailyMenuResponse> responses = new ArrayList<>(menus.size());
        for (DailyMenuEntity menu : menus) {
            List<DailyMenuEntryEntity> entries =
                    entriesByMenuId.getOrDefault(menu.getId(), List.of());
            List<com.countin.countin_backend.meal.api.dto.response.DailyMenuOptionResponse> options =
                    new ArrayList<>(entries.size());
            for (DailyMenuEntryEntity entry : entries) {
                if (entry.getEntryType() == DailyMenuEntryType.PACKAGE) {
                    options.add(com.countin.countin_backend.meal.api.dto.response.DailyMenuOptionResponse.from(
                            entry, packageItemsByEntryId.getOrDefault(entry.getId(), List.of())));
                } else {
                    options.add(com.countin.countin_backend.meal.api.dto.response.DailyMenuOptionResponse.from(entry));
                }
            }
            responses.add(DailyMenuResponse.builder()
                    .dailyMenuId(menu.getId())
                    .menuDate(menu.getMenuDate())
                    .mealType(menu.getMealType())
                    .status(menu.getStatus())
                    .publishedAt(menu.getPublishedAt())
                    .notes(menu.getNotes())
                    .options(options)
                    .build());
        }
        return responses;
    }

    private DailyMenuResponse toResponse(DailyMenuEntity menu) {
        List<DailyMenuResponse> responses = toResponses(List.of(menu));
        return responses.isEmpty()
                ? DailyMenuResponse.builder()
                        .dailyMenuId(menu.getId())
                        .menuDate(menu.getMenuDate())
                        .mealType(menu.getMealType())
                        .status(menu.getStatus())
                        .publishedAt(menu.getPublishedAt())
                        .notes(menu.getNotes())
                        .options(List.of())
                        .build()
                : responses.get(0);
    }

    private DailyMenuEntity loadMenu(UUID spaceId, LocalDate date, MealType mealType) {
        return dailyMenuRepository
                .findBySpaceDateAndType(spaceId, date, mealType)
                .filter(menu -> !menu.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "DailyMenu", "date/mealType", date + "/" + mealType));
    }

    private void syncEntries(UUID spaceId, DailyMenuEntity menu, List<DailyMenuOptionRequest> options) {
        List<DailyMenuEntryEntity> existing = dailyMenuEntryRepository.findByDailyMenuId(menu.getId());
        Set<UUID> pollReferencedIds = mealPollOptionRepository.findReferencedEntryIdsByDailyMenuId(menu.getId());
        Set<UUID> claimedIds = new HashSet<>();
        List<DailyMenuEntryEntity> unclaimed = new ArrayList<>(existing);

        for (DailyMenuOptionRequest option : options) {
            DailyMenuEntryEntity entry = resolveEntryForOption(menu, option, unclaimed, claimedIds);
            DailyMenuEntryType entryType = applyScalarsToEntry(spaceId, entry, option);
            DailyMenuEntryEntity saved = dailyMenuEntryRepository.save(entry);
            if (entryType == DailyMenuEntryType.PACKAGE) {
                dailyMenuPackageItemRepository.deleteByEntryId(saved.getId());
                savePackageItems(spaceId, saved, option.getItemIds());
            }
            claimedIds.add(saved.getId());
            UUID savedId = saved.getId();
            unclaimed.removeIf(e -> e.getId().equals(savedId));
        }

        for (DailyMenuEntryEntity orphan : unclaimed) {
            if (pollReferencedIds.contains(orphan.getId())) {
                orphan.setAvailable(false);
                dailyMenuEntryRepository.save(orphan);
            } else {
                removeEntry(orphan);
            }
        }
    }

    private void removeUnreferencedEntries(UUID dailyMenuId) {
        Set<UUID> pollReferencedIds = mealPollOptionRepository.findReferencedEntryIdsByDailyMenuId(dailyMenuId);
        for (DailyMenuEntryEntity entry : dailyMenuEntryRepository.findByDailyMenuId(dailyMenuId)) {
            if (!pollReferencedIds.contains(entry.getId())) {
                removeEntry(entry);
            } else {
                entry.setAvailable(false);
                dailyMenuEntryRepository.save(entry);
            }
        }
    }

    private void removeEntry(DailyMenuEntryEntity entry) {
        dailyMenuPackageItemRepository.deleteByEntryId(entry.getId());
        dailyMenuEntryRepository.delete(entry);
    }

    private DailyMenuEntryEntity resolveEntryForOption(
            DailyMenuEntity menu,
            DailyMenuOptionRequest option,
            List<DailyMenuEntryEntity> unclaimed,
            Set<UUID> claimedIds) {
        if (option.getOptionId() != null) {
            return unclaimed.stream()
                    .filter(entry -> entry.getId().equals(option.getOptionId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "DailyMenuEntry", "optionId", option.getOptionId()));
        }

        DailyMenuEntryType entryType = resolveEntryType(option);
        return unclaimed.stream()
                .filter(entry -> !claimedIds.contains(entry.getId()))
                .filter(entry -> entry.getEntryType() == entryType)
                .filter(entry -> matchesCatalogRef(entry, option, entryType))
                .findFirst()
                .orElseGet(() -> DailyMenuEntryEntity.builder().dailyMenu(menu).build());
    }

    private boolean matchesCatalogRef(
            DailyMenuEntryEntity entry, DailyMenuOptionRequest option, DailyMenuEntryType entryType) {
        return switch (entryType) {
            case COMBO -> entry.getCombo() != null
                    && option.getComboId() != null
                    && entry.getCombo().getId().equals(option.getComboId());
            case ITEM -> entry.getItem() != null
                    && option.getItemId() != null
                    && entry.getItem().getId().equals(option.getItemId());
            case PACKAGE -> matchesPackageRef(entry, option);
        };
    }

    private boolean matchesPackageRef(DailyMenuEntryEntity entry, DailyMenuOptionRequest option) {
        if (entry.getEntryType() != DailyMenuEntryType.PACKAGE) {
            return false;
        }
        List<UUID> requestedIds = option.getItemIds() == null ? List.of() : option.getItemIds();
        if (requestedIds.isEmpty()) {
            return false;
        }
        List<UUID> existingIds = dailyMenuPackageItemRepository.findByEntryIdWithItems(entry.getId()).stream()
                .map(packageItem -> packageItem.getItem().getId())
                .toList();
        if (existingIds.size() != requestedIds.size()) {
            return false;
        }
        return new HashSet<>(existingIds).equals(new HashSet<>(requestedIds));
    }

    private DailyMenuEntryType applyScalarsToEntry(
            UUID spaceId, DailyMenuEntryEntity entry, DailyMenuOptionRequest option) {
        MealComboEntity combo = null;
        FoodItemEntity item = null;
        DailyMenuEntryType entryType = resolveEntryType(option);

        if (entryType == DailyMenuEntryType.COMBO) {
            combo = mealComboService.loadCombo(spaceId, option.getComboId());
            if (!combo.isActive()) {
                throw new BusinessException("Combo is not active", HttpStatus.BAD_REQUEST);
            }
        } else if (entryType == DailyMenuEntryType.ITEM) {
            item = foodCatalogService.loadEnabledItemForSpace(spaceId, option.getItemId());
        }

        entry.setEntryType(entryType);
        entry.setCombo(combo);
        entry.setItem(item);
        entry.setLabel(option.getLabel().trim());
        entry.setSortOrder(option.getSortOrder());
        entry.setAvailable(option.isAvailable());
        if (entryType == DailyMenuEntryType.PACKAGE) {
            MealPriceValidator.validateOptionalPrice(option.getPrice());
            entry.setPrice(option.getPrice());
            entry.setCurrencyCode(MealPriceValidator.resolveCurrencyCode(option.getCurrencyCode()));
        } else {
            entry.setPrice(null);
            entry.setCurrencyCode("INR");
        }
        return entryType;
    }

    private void savePackageItems(UUID spaceId, DailyMenuEntryEntity entry, List<UUID> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            throw new BusinessException("itemIds are required for PACKAGE entries", HttpStatus.BAD_REQUEST);
        }
        int sortOrder = 1;
        for (UUID itemId : itemIds) {
            FoodItemEntity foodItem = foodCatalogService.loadEnabledItemForSpace(spaceId, itemId);
            dailyMenuPackageItemRepository.save(DailyMenuPackageItemEntity.builder()
                    .entry(entry)
                    .item(foodItem)
                    .sortOrder(sortOrder++)
                    .build());
        }
    }

    private void copyPackageItems(UUID sourceEntryId, DailyMenuEntryEntity targetEntry) {
        List<DailyMenuPackageItemEntity> sourceItems =
                dailyMenuPackageItemRepository.findByEntryIdWithItems(sourceEntryId);
        int sortOrder = 1;
        for (DailyMenuPackageItemEntity sourceItem : sourceItems) {
            dailyMenuPackageItemRepository.save(DailyMenuPackageItemEntity.builder()
                    .entry(targetEntry)
                    .item(sourceItem.getItem())
                    .sortOrder(sortOrder++)
                    .build());
        }
    }

    private DailyMenuEntryType resolveEntryType(DailyMenuOptionRequest option) {
        if (option.getEntryType() != null) {
            if (option.getEntryType() == DailyMenuEntryType.COMBO) {
                if (option.getComboId() == null) {
                    throw new BusinessException("comboId is required for COMBO entries", HttpStatus.BAD_REQUEST);
                }
                if (option.getItemId() != null) {
                    throw new BusinessException("itemId must be null for COMBO entries", HttpStatus.BAD_REQUEST);
                }
                return DailyMenuEntryType.COMBO;
            }
            if (option.getEntryType() == DailyMenuEntryType.PACKAGE) {
                if (option.getComboId() != null || option.getItemId() != null) {
                    throw new BusinessException(
                            "comboId and itemId must be null for PACKAGE entries", HttpStatus.BAD_REQUEST);
                }
                if (option.getItemIds() == null || option.getItemIds().isEmpty()) {
                    throw new BusinessException("itemIds are required for PACKAGE entries", HttpStatus.BAD_REQUEST);
                }
                return DailyMenuEntryType.PACKAGE;
            }
            if (option.getItemId() == null) {
                throw new BusinessException("itemId is required for ITEM entries", HttpStatus.BAD_REQUEST);
            }
            if (option.getComboId() != null) {
                throw new BusinessException("comboId must be null for ITEM entries", HttpStatus.BAD_REQUEST);
            }
            return DailyMenuEntryType.ITEM;
        }
        if (option.getComboId() != null) {
            if (option.getItemId() != null) {
                throw new BusinessException("Provide either comboId or itemId, not both", HttpStatus.BAD_REQUEST);
            }
            return DailyMenuEntryType.COMBO;
        }
        if (option.getItemId() != null) {
            return DailyMenuEntryType.ITEM;
        }
        if (option.getItemIds() != null && !option.getItemIds().isEmpty()) {
            return DailyMenuEntryType.PACKAGE;
        }
        throw new BusinessException("entryType or comboId/itemId/itemIds is required", HttpStatus.BAD_REQUEST);
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessException("'from' must be on or before 'to'", HttpStatus.BAD_REQUEST);
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_DATE_RANGE_DAYS) {
            throw new BusinessException("Date range cannot exceed 31 days", HttpStatus.BAD_REQUEST);
        }
    }

    private SpaceEntity loadSpace(UUID spaceId) {
        return spaceRepository
                .findById(spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Space", "id", spaceId));
    }
}
