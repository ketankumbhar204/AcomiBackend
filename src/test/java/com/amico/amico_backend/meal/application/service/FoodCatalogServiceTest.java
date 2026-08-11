package com.amico.amico_backend.meal.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amico.amico_backend.common.exception.BusinessException;
import com.amico.amico_backend.meal.api.dto.request.CreateFoodItemRequest;
import com.amico.amico_backend.meal.api.dto.request.UpdateFoodItemDefaultPriceRequest;
import com.amico.amico_backend.meal.domain.model.FoodScope;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.FoodCategoryEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.FoodItemEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.SpaceFoodItemSettingsEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.repository.FoodCategoryRepository;
import com.amico.amico_backend.meal.infrastructure.persistence.repository.FoodItemRepository;
import com.amico.amico_backend.meal.infrastructure.persistence.repository.SpaceFoodCategorySettingsRepository;
import com.amico.amico_backend.meal.infrastructure.persistence.repository.SpaceFoodItemSettingsRepository;
import com.amico.amico_backend.member.application.service.SpaceMembershipResolver;
import com.amico.amico_backend.member.domain.model.MembershipRole;
import com.amico.amico_backend.member.domain.model.MembershipStatus;
import com.amico.amico_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.amico.amico_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.amico.amico_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.amico.amico_backend.space.domain.model.SpaceType;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.amico.amico_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.amico.amico_backend.user.infrastructure.persistence.entity.UserEntity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class FoodCatalogServiceTest {

    @Mock
    private FoodCategoryRepository foodCategoryRepository;

    @Mock
    private FoodItemRepository foodItemRepository;

    @Mock
    private SpaceFoodItemSettingsRepository spaceFoodItemSettingsRepository;

    @Mock
    private SpaceFoodCategorySettingsRepository spaceFoodCategorySettingsRepository;

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private SpaceMembershipRepository spaceMembershipRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MealSpaceSetupService mealSpaceSetupService;

    private FoodCatalogService foodCatalogService;

    private UUID spaceId;
    private UUID otherSpaceId;
    private UUID callerId;

    @BeforeEach
    void setUp() {
        foodCatalogService = new FoodCatalogService(
                foodCategoryRepository,
                foodItemRepository,
                spaceFoodItemSettingsRepository,
                spaceFoodCategorySettingsRepository,
                spaceRepository,
                new MealAccessService(new SpaceMembershipResolver(spaceMembershipRepository), memberRepository),
                mealSpaceSetupService);
        spaceId = UUID.randomUUID();
        otherSpaceId = UUID.randomUUID();
        callerId = UUID.randomUUID();
    }

    @Test
    void listCategories_returnsGlobalCategoriesWithItemCounts() {
        stubOwnerMembership();
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space()));
        when(foodCategoryRepository.countGlobalActive()).thenReturn(12L);
        FoodCategoryEntity breads = category("Breads", FoodScope.GLOBAL, 1);
        when(foodCategoryRepository.findVisibleForSpace(spaceId)).thenReturn(List.of(breads));
        when(foodItemRepository.countVisibleItemsGroupedByCategory(spaceId))
                .thenReturn(List.of(categoryItemCount(breads.getId(), 8L)));

        var responses = foodCatalogService.listCategories(spaceId, callerId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getName()).isEqualTo("Breads");
        assertThat(responses.get(0).getScope()).isEqualTo(FoodScope.GLOBAL);
        assertThat(responses.get(0).getItemCount()).isEqualTo(8);
        verify(mealSpaceSetupService).ensureSampleCombos(any(SpaceEntity.class));
    }

    @Test
    void getFoodCategories_returnsGlobalCategories_forExistingMessSpace() {
        stubOwnerMembership();
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space()));
        when(foodCategoryRepository.countGlobalActive()).thenReturn(12L);
        List<FoodCategoryEntity> globalCategories = java.util.stream.IntStream.rangeClosed(1, 12)
                .mapToObj(i -> category("Category " + i, FoodScope.GLOBAL, i))
                .toList();
        when(foodCategoryRepository.findVisibleForSpace(spaceId)).thenReturn(globalCategories);
        when(foodItemRepository.countVisibleItemsGroupedByCategory(spaceId))
                .thenReturn(globalCategories.stream()
                        .map(category -> categoryItemCount(category.getId(), 5L))
                        .toList());

        var responses = foodCatalogService.listCategories(spaceId, callerId);

        assertThat(responses).hasSize(12);
        assertThat(responses).allMatch(response -> response.getScope() == FoodScope.GLOBAL);
    }

    @Test
    void getFoodItems_returnsGlobalSeed_withoutSpaceCopy() {
        stubOwnerMembership();
        when(foodCategoryRepository.countGlobalActive()).thenReturn(12L);
        FoodCategoryEntity breads = category("Breads", FoodScope.GLOBAL, 1);
        List<FoodItemEntity> globalItems = java.util.stream.IntStream.rangeClosed(1, 62)
                .mapToObj(i -> FoodItemEntity.builder()
                        .category(breads)
                        .name(i == 1 ? "Chapati" : "Item " + i)
                        .scope(FoodScope.GLOBAL)
                        .isActive(true)
                        .isCustom(false)
                        .build())
                .toList();
        when(foodItemRepository.findAllVisibleForSpace(spaceId)).thenReturn(globalItems);
        when(spaceFoodItemSettingsRepository.findAllBySpaceId(spaceId)).thenReturn(List.of());

        var items = foodCatalogService.listItems(spaceId, callerId, null);

        assertThat(items).hasSizeGreaterThanOrEqualTo(60);
        assertThat(items).anyMatch(item -> "Chapati".equals(item.getName()));
        assertThat(items).allMatch(item -> item.getScope() == FoodScope.GLOBAL || !item.isCustom());
    }

    @Test
    void createItem_marksCustomSpaceItem() {
        stubOwnerMembership();
        UUID categoryId = UUID.randomUUID();
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space()));
        FoodCategoryEntity category = category("Dal", FoodScope.GLOBAL, 3);
        category.setId(categoryId);
        when(foodCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));

        FoodItemEntity saved = FoodItemEntity.builder()
                .category(category)
                .name("Mess Special Dal")
                .scope(FoodScope.SPACE)
                .space(space())
                .isActive(true)
                .isCustom(true)
                .build();
        saved.setId(UUID.randomUUID());
        when(foodItemRepository.save(any(FoodItemEntity.class))).thenReturn(saved);
        when(foodItemRepository.findByIdWithCategory(saved.getId())).thenReturn(Optional.of(saved));

        CreateFoodItemRequest request = new CreateFoodItemRequest();
        request.setCategoryId(categoryId);
        request.setName("Mess Special Dal");

        var response = foodCatalogService.createItem(spaceId, callerId, request);

        assertThat(response.isCustom()).isTrue();
        assertThat(response.getScope()).isEqualTo(FoodScope.SPACE);
    }

    @Test
    void deactivateGlobalItem_hidesForSpaceViaSettings() {
        stubOwnerMembership();
        UUID itemId = UUID.randomUUID();
        FoodCategoryEntity category = category("Dal", FoodScope.GLOBAL, 3);
        FoodItemEntity item = FoodItemEntity.builder()
                .category(category)
                .name("Dal Fry")
                .scope(FoodScope.GLOBAL)
                .isActive(true)
                .isCustom(false)
                .build();
        item.setId(itemId);
        when(foodItemRepository.findByIdWithCategory(itemId)).thenReturn(Optional.of(item));
        when(spaceFoodItemSettingsRepository.findBySpaceIdAndItemId(spaceId, itemId))
                .thenReturn(Optional.empty());

        foodCatalogService.deactivateItem(spaceId, itemId, callerId);

        verify(spaceFoodItemSettingsRepository).save(org.mockito.ArgumentMatchers.argThat(
                settings -> !settings.isEnabled() && settings.getSpaceId().equals(spaceId)));
        verify(foodItemRepository, never()).save(item);
    }

    @Test
    void deactivateGlobalItem_onlyAffectsRequestedSpace() {
        stubOwnerMembership();
        UUID itemId = UUID.randomUUID();
        FoodItemEntity item = globalItem(itemId, "Chapati");
        when(foodItemRepository.findByIdWithCategory(itemId)).thenReturn(Optional.of(item));
        when(spaceFoodItemSettingsRepository.findBySpaceIdAndItemId(spaceId, itemId))
                .thenReturn(Optional.empty());

        foodCatalogService.deactivateItem(spaceId, itemId, callerId);

        verify(spaceFoodItemSettingsRepository).save(org.mockito.ArgumentMatchers.argThat(
                settings -> settings.getSpaceId().equals(spaceId) && !settings.isEnabled()));
        verify(spaceFoodItemSettingsRepository, never()).findBySpaceIdAndItemId(otherSpaceId, itemId);
    }

    @Test
    void deactivateGlobalCategory_hidesCategoryForSpace() {
        stubOwnerMembership();
        UUID categoryId = UUID.randomUUID();
        FoodCategoryEntity category = category("Breads", FoodScope.GLOBAL, 1);
        category.setId(categoryId);
        when(foodCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(spaceFoodCategorySettingsRepository.findBySpaceIdAndCategoryId(spaceId, categoryId))
                .thenReturn(Optional.empty());

        foodCatalogService.deactivateCategory(spaceId, categoryId, callerId);

        verify(spaceFoodCategorySettingsRepository).save(org.mockito.ArgumentMatchers.argThat(
                settings -> settings.getSpaceId().equals(spaceId)
                        && settings.getCategoryId().equals(categoryId)
                        && !settings.isEnabled()));
        verify(foodCategoryRepository, never()).save(category);
    }

    @Test
    void deactivateSpaceCategory_deactivatesCategoryAndItems() {
        stubOwnerMembership();
        SpaceEntity space = space();
        UUID categoryId = UUID.randomUUID();
        FoodCategoryEntity category = FoodCategoryEntity.builder()
                .name("Custom")
                .scope(FoodScope.SPACE)
                .space(space)
                .sortOrder(99)
                .isActive(true)
                .build();
        category.setId(categoryId);
        FoodItemEntity item = FoodItemEntity.builder()
                .category(category)
                .name("Custom Item")
                .scope(FoodScope.SPACE)
                .space(space)
                .isActive(true)
                .isCustom(true)
                .build();
        when(foodCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(foodItemRepository.findActiveSpaceItemsInCategory(spaceId, categoryId)).thenReturn(List.of(item));
        when(foodCategoryRepository.save(category)).thenReturn(category);
        when(foodItemRepository.save(item)).thenReturn(item);

        foodCatalogService.deactivateCategory(spaceId, categoryId, callerId);

        assertThat(category.isActive()).isFalse();
        assertThat(item.isActive()).isFalse();
        verify(foodCategoryRepository).save(category);
        verify(foodItemRepository).save(item);
    }

    @Test
    void deactivateSpaceItem_setsInactive() {
        stubOwnerMembership();
        SpaceEntity space = space();
        UUID itemId = UUID.randomUUID();
        FoodCategoryEntity category = category("Custom", FoodScope.SPACE, 1);
        FoodItemEntity item = FoodItemEntity.builder()
                .category(category)
                .name("Custom Item")
                .scope(FoodScope.SPACE)
                .space(space)
                .isActive(true)
                .isCustom(true)
                .build();
        item.setId(itemId);
        when(foodItemRepository.findByIdWithCategory(itemId)).thenReturn(Optional.of(item));
        when(foodItemRepository.save(item)).thenReturn(item);

        foodCatalogService.deactivateItem(spaceId, itemId, callerId);

        assertThat(item.isActive()).isFalse();
        verify(spaceFoodItemSettingsRepository, never()).save(any());
    }

    @Test
    void deactivateCategory_deniesStaff() {
        stubMembership(MembershipRole.STAFF);

        assertThatThrownBy(() -> foodCatalogService.deactivateCategory(spaceId, UUID.randomUUID(), callerId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Only OWNER or MANAGER can manage meals");
    }

    @Test
    void deactivateItem_deniesTenant() {
        stubMembership(MembershipRole.TENANT);

        assertThatThrownBy(() -> foodCatalogService.deactivateItem(spaceId, UUID.randomUUID(), callerId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Only OWNER or MANAGER can manage meals");
    }

    @Test
    void deactivateSpaceCategoryFromOtherSpace_returnsForbidden() {
        stubOwnerMembership();
        UUID categoryId = UUID.randomUUID();
        SpaceEntity otherSpace = SpaceEntity.builder()
                .owner(space().getOwner())
                .name("Other")
                .type(SpaceType.MESS)
                .isActive(true)
                .build();
        otherSpace.setId(otherSpaceId);
        FoodCategoryEntity category = FoodCategoryEntity.builder()
                .name("Other Category")
                .scope(FoodScope.SPACE)
                .space(otherSpace)
                .sortOrder(1)
                .isActive(true)
                .build();
        category.setId(categoryId);
        when(foodCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> foodCatalogService.deactivateCategory(spaceId, categoryId, callerId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void listItems_returnsSpaceSpecificDefaultPrices() {
        stubMembershipForSpace(spaceId, MembershipRole.OWNER);
        stubMembershipForSpace(otherSpaceId, MembershipRole.OWNER);
        UUID itemId = UUID.randomUUID();
        FoodItemEntity poha = globalItem(itemId, "Poha");
        when(foodCategoryRepository.countGlobalActive()).thenReturn(12L);
        when(foodItemRepository.findAllVisibleForSpace(spaceId)).thenReturn(List.of(poha));
        when(foodItemRepository.findAllVisibleForSpace(otherSpaceId)).thenReturn(List.of(poha));

        SpaceFoodItemSettingsEntity spaceASettings = SpaceFoodItemSettingsEntity.builder()
                .spaceId(spaceId)
                .itemId(itemId)
                .isEnabled(true)
                .defaultPrice(new BigDecimal("30.00"))
                .currencyCode("INR")
                .build();
        when(spaceFoodItemSettingsRepository.findAllBySpaceId(spaceId)).thenReturn(List.of(spaceASettings));
        when(spaceFoodItemSettingsRepository.findAllBySpaceId(otherSpaceId)).thenReturn(List.of());

        var spaceAItems = foodCatalogService.listItems(spaceId, callerId, null);
        var spaceBItems = foodCatalogService.listItems(otherSpaceId, callerId, null);

        assertThat(spaceAItems).hasSize(1);
        assertThat(spaceAItems.get(0).getDefaultPrice()).isEqualByComparingTo("30.00");
        assertThat(spaceBItems).hasSize(1);
        assertThat(spaceBItems.get(0).getDefaultPrice()).isNull();
    }

    @Test
    void updateItemDefaultPrice_persistsPerSpaceSetting() {
        stubOwnerMembership();
        UUID itemId = UUID.randomUUID();
        FoodItemEntity item = globalItem(itemId, "Poha");
        when(foodItemRepository.findByIdWithCategory(itemId)).thenReturn(Optional.of(item));
        when(spaceFoodItemSettingsRepository.findBySpaceIdAndItemId(spaceId, itemId))
                .thenReturn(Optional.empty());
        when(spaceFoodItemSettingsRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateFoodItemDefaultPriceRequest request = new UpdateFoodItemDefaultPriceRequest();
        request.setPrice(new BigDecimal("30.00"));

        var response = foodCatalogService.updateItemDefaultPrice(spaceId, itemId, callerId, request);

        assertThat(response.getDefaultPrice()).isEqualByComparingTo("30.00");
        assertThat(response.getCurrencyCode()).isEqualTo("INR");
        verify(spaceFoodItemSettingsRepository).save(org.mockito.ArgumentMatchers.argThat(settings ->
                settings.getSpaceId().equals(spaceId)
                        && settings.getItemId().equals(itemId)
                        && settings.getDefaultPrice().compareTo(new BigDecimal("30.00")) == 0));
    }

    @Test
    void createItem_deniesStaff() {
        stubMembership(MembershipRole.STAFF);
        CreateFoodItemRequest request = new CreateFoodItemRequest();
        request.setCategoryId(UUID.randomUUID());
        request.setName("Item");

        assertThatThrownBy(() -> foodCatalogService.createItem(spaceId, callerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Only OWNER or MANAGER can manage meals");
    }

    private FoodItemEntity globalItem(UUID itemId, String name) {
        FoodCategoryEntity category = category("Breads", FoodScope.GLOBAL, 1);
        FoodItemEntity item = FoodItemEntity.builder()
                .category(category)
                .name(name)
                .scope(FoodScope.GLOBAL)
                .isActive(true)
                .isCustom(false)
                .build();
        item.setId(itemId);
        return item;
    }

    private void stubOwnerMembership() {
        stubMembership(MembershipRole.OWNER);
    }

    private void stubMembership(MembershipRole role) {
        stubMembershipForSpace(spaceId, role);
    }

    private void stubMembershipForSpace(UUID targetSpaceId, MembershipRole role) {
        UserEntity user = UserEntity.builder().fullName("User").mobileNumber("9000000000").build();
        user.setId(callerId);
        SpaceEntity space = spaceForId(targetSpaceId);
        SpaceMembershipEntity membership = SpaceMembershipEntity.builder()
                .user(user)
                .space(space)
                .role(role)
                .status(MembershipStatus.ACTIVE)
                .build();
        when(spaceMembershipRepository.findMembershipByUserAndSpace(callerId, targetSpaceId))
                .thenReturn(Optional.of(membership));
    }

    private SpaceEntity space() {
        return spaceForId(spaceId);
    }

    private SpaceEntity spaceForId(UUID targetSpaceId) {
        UserEntity owner = UserEntity.builder().fullName("Owner").mobileNumber("9000000001").build();
        owner.setId(UUID.randomUUID());
        SpaceEntity space = SpaceEntity.builder()
                .owner(owner)
                .name("Mess")
                .type(SpaceType.MESS)
                .isActive(true)
                .build();
        space.setId(targetSpaceId);
        return space;
    }

    private FoodCategoryEntity category(String name, FoodScope scope, int sortOrder) {
        FoodCategoryEntity category = FoodCategoryEntity.builder()
                .name(name)
                .scope(scope)
                .sortOrder(sortOrder)
                .isActive(true)
                .build();
        category.setId(UUID.randomUUID());
        return category;
    }

    private FoodItemRepository.CategoryItemCount categoryItemCount(UUID categoryId, long itemCount) {
        return new CategoryItemCountStub(categoryId, itemCount);
    }

    private static final class CategoryItemCountStub implements FoodItemRepository.CategoryItemCount {
        private final UUID categoryId;
        private final long itemCount;

        private CategoryItemCountStub(UUID categoryId, long itemCount) {
            this.categoryId = categoryId;
            this.itemCount = itemCount;
        }

        @Override
        public UUID getCategoryId() {
            return categoryId;
        }

        @Override
        public long getItemCount() {
            return itemCount;
        }
    }
}
