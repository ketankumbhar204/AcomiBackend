package com.amico.amico_backend.meal.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amico.amico_backend.common.exception.BusinessException;
import com.amico.amico_backend.meal.api.dto.request.CreateComboInlineItemRequest;
import com.amico.amico_backend.meal.api.dto.request.CreateMealComboRequest;
import com.amico.amico_backend.meal.api.dto.request.UpdateMealComboRequest;
import com.amico.amico_backend.meal.domain.model.FoodScope;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.FoodItemEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealComboEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealComboItemEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.repository.MealComboItemRepository;
import com.amico.amico_backend.meal.infrastructure.persistence.repository.MealComboRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MealComboServiceTest {

    @Mock
    private MealComboRepository mealComboRepository;

    @Mock
    private MealComboItemRepository mealComboItemRepository;

    @Mock
    private FoodCatalogService foodCatalogService;

    @Mock
    private MealSpaceSetupService mealSpaceSetupService;

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private SpaceMembershipRepository spaceMembershipRepository;

    @Mock
    private MemberRepository memberRepository;

    private MealComboService mealComboService;

    private UUID spaceId;
    private UUID callerId;

    @BeforeEach
    void setUp() {
        mealComboService = new MealComboService(
                mealComboRepository,
                mealComboItemRepository,
                foodCatalogService,
                mealSpaceSetupService,
                spaceRepository,
                new MealAccessService(new SpaceMembershipResolver(spaceMembershipRepository), memberRepository));
        spaceId = UUID.randomUUID();
        callerId = UUID.randomUUID();
    }

    @Test
    void createCombo_withInlineNewItems_createsItemsAndCombo() {
        stubOwnerMembership();
        SpaceEntity space = space();
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        UUID categoryId = UUID.randomUUID();
        UUID newItemId = UUID.randomUUID();
        CreateMealComboRequest request = new CreateMealComboRequest();
        request.setName("Sunday Special");
        request.setDescription("Mess only");
        CreateComboInlineItemRequest inlineItem = new CreateComboInlineItemRequest();
        inlineItem.setCategoryId(categoryId);
        inlineItem.setName("Mess Special Dal");
        request.setNewItems(List.of(inlineItem));

        when(mealComboRepository.save(any(MealComboEntity.class))).thenAnswer(invocation -> {
            MealComboEntity combo = invocation.getArgument(0);
            combo.setId(UUID.randomUUID());
            return combo;
        });
        when(foodCatalogService.createItem(any(), any(), any())).thenAnswer(invocation -> {
            var response = com.amico.amico_backend.meal.api.dto.response.FoodItemResponse.builder()
                    .itemId(newItemId)
                    .name("Mess Special Dal")
                    .build();
            return response;
        });
        when(foodCatalogService.loadEnabledItemForSpace(spaceId, newItemId))
                .thenReturn(FoodItemEntity.builder().name("Mess Special Dal").build());
        when(mealComboItemRepository.findByComboIdWithItems(any())).thenReturn(List.of());

        mealComboService.createCombo(spaceId, callerId, request);

        verify(foodCatalogService).createItem(eq(spaceId), eq(callerId), org.mockito.ArgumentMatchers.argThat(
                itemRequest -> itemRequest.getCategoryId().equals(categoryId)
                        && itemRequest.getName().equals("Mess Special Dal")));
        verify(mealComboItemRepository).save(any(MealComboItemEntity.class));
    }

    @Test
    void updateCombo_replacesItemsAfterDelete() {
        stubOwnerMembership();
        SpaceEntity space = space();
        UUID comboId = UUID.randomUUID();
        UUID rotiId = UUID.fromString("22222222-2222-2222-2222-222222220002");
        UUID dalId = UUID.fromString("22222222-2222-2222-2222-222222220071");
        MealComboEntity combo = MealComboEntity.builder()
                .space(space)
                .name("Cmobo 1")
                .isActive(true)
                .build();
        combo.setId(comboId);
        when(mealComboRepository.findByIdAndSpaceId(comboId, spaceId)).thenReturn(Optional.of(combo));
        when(mealComboRepository.save(combo)).thenReturn(combo);
        when(foodCatalogService.loadEnabledItemForSpace(spaceId, rotiId))
                .thenReturn(FoodItemEntity.builder().name("Roti").build());
        when(foodCatalogService.loadEnabledItemForSpace(spaceId, dalId))
                .thenReturn(FoodItemEntity.builder().name("Plain Daal").build());
        when(mealComboItemRepository.findByComboIdWithItems(comboId)).thenReturn(List.of());

        UpdateMealComboRequest request = new UpdateMealComboRequest();
        request.setName("Cmobo 1");
        request.setItemIds(List.of(rotiId, dalId));

        mealComboService.updateCombo(spaceId, comboId, callerId, request);

        var order = inOrder(mealComboRepository, mealComboItemRepository);
        order.verify(mealComboRepository).save(combo);
        order.verify(mealComboItemRepository).deleteByComboId(comboId);
        verify(mealComboItemRepository, org.mockito.Mockito.times(2)).save(any(MealComboItemEntity.class));
    }

    @Test
    void updateComboPrice_persistsPerSpaceCombo() {
        stubOwnerMembership();
        SpaceEntity space = space();
        UUID comboId = UUID.randomUUID();
        MealComboEntity combo = MealComboEntity.builder()
                .space(space)
                .name("Standard Lunch Thali")
                .isActive(true)
                .build();
        combo.setId(comboId);
        when(mealComboRepository.findByIdAndSpaceId(comboId, spaceId)).thenReturn(Optional.of(combo));
        when(mealComboRepository.save(combo)).thenReturn(combo);
        when(mealComboItemRepository.findByComboIdWithItems(comboId)).thenReturn(List.of());

        var request = new com.amico.amico_backend.meal.api.dto.request.UpdateMealComboPriceRequest();
        request.setPrice(new java.math.BigDecimal("120.00"));

        var response = mealComboService.updateComboPrice(spaceId, comboId, callerId, request);

        assertThat(response.getPrice()).isEqualByComparingTo("120.00");
        assertThat(combo.getPrice()).isEqualByComparingTo("120.00");
        verify(mealComboRepository).save(combo);
        verify(mealComboItemRepository, org.mockito.Mockito.never()).deleteByComboId(comboId);
    }

    @Test
    void deactivateCombo_setsInactive() {
        stubOwnerMembership();
        SpaceEntity space = space();
        UUID comboId = UUID.randomUUID();
        MealComboEntity combo = MealComboEntity.builder()
                .space(space)
                .name("Standard Lunch Thali")
                .description("Daily lunch combo")
                .isActive(true)
                .build();
        combo.setId(comboId);
        when(mealComboRepository.findByIdAndSpaceId(comboId, spaceId)).thenReturn(Optional.of(combo));
        when(mealComboRepository.save(combo)).thenReturn(combo);

        mealComboService.deactivateCombo(spaceId, comboId, callerId);

        assertThat(combo.isActive()).isFalse();
        verify(mealComboRepository).save(combo);
    }

    @Test
    void deactivateCombo_deniesStaff() {
        stubMembership(MembershipRole.STAFF);

        assertThatThrownBy(() -> mealComboService.deactivateCombo(spaceId, UUID.randomUUID(), callerId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Only OWNER or MANAGER can manage meals");
    }

    @Test
    void backfillSampleCombos_createsSampleCombos_whenEmpty() {
        stubOwnerMembership();
        SpaceEntity space = space();
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));

        MealComboEntity thali = MealComboEntity.builder()
                .space(space)
                .name("Standard Lunch Thali")
                .description("Daily lunch combo")
                .isActive(true)
                .build();
        thali.setId(UUID.randomUUID());
        MealComboEntity dalRice = MealComboEntity.builder()
                .space(space)
                .name("Dal Rice Combo")
                .description("Simple dal rice")
                .isActive(true)
                .build();
        dalRice.setId(UUID.randomUUID());

        when(mealComboRepository.findBySpaceIdAndIsActiveTrueOrderByNameAsc(spaceId))
                .thenReturn(List.of(dalRice, thali));
        when(mealComboItemRepository.findByComboIdWithItems(thali.getId())).thenReturn(List.of());
        when(mealComboItemRepository.findByComboIdWithItems(dalRice.getId())).thenReturn(List.of());

        var combos = mealComboService.listCombos(spaceId, callerId);

        verify(mealSpaceSetupService).ensureSampleCombos(space);
        assertThat(combos).hasSizeGreaterThanOrEqualTo(2);
        assertThat(combos).extracting("name").contains("Standard Lunch Thali", "Dal Rice Combo");
        assertThat(combos).allMatch(combo -> combo.getScope() == FoodScope.SPACE);
    }

    private void stubOwnerMembership() {
        stubMembership(MembershipRole.OWNER);
    }

    private void stubMembership(MembershipRole role) {
        UserEntity user = UserEntity.builder().fullName("Owner").mobileNumber("9000000000").build();
        user.setId(callerId);
        SpaceEntity space = space();
        SpaceMembershipEntity membership = SpaceMembershipEntity.builder()
                .user(user)
                .space(space)
                .role(role)
                .status(MembershipStatus.ACTIVE)
                .build();
        when(spaceMembershipRepository.findMembershipByUserAndSpace(callerId, spaceId))
                .thenReturn(Optional.of(membership));
    }

    private SpaceEntity space() {
        UserEntity owner = UserEntity.builder().fullName("Owner").mobileNumber("9000000001").build();
        owner.setId(UUID.randomUUID());
        SpaceEntity space = SpaceEntity.builder()
                .owner(owner)
                .name("Test Mess 1")
                .type(SpaceType.MESS)
                .isActive(true)
                .build();
        space.setId(spaceId);
        return space;
    }
}
