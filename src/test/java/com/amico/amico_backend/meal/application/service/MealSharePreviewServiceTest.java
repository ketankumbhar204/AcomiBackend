package com.amico.amico_backend.meal.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.amico.amico_backend.common.exception.BusinessException;
import com.amico.amico_backend.meal.domain.model.DailyMenuEntryType;
import com.amico.amico_backend.meal.domain.model.DailyMenuStatus;
import com.amico.amico_backend.meal.domain.model.MealPlanCode;
import com.amico.amico_backend.meal.domain.model.MealParticipationStatus;
import com.amico.amico_backend.meal.domain.model.MealType;
import com.amico.amico_backend.meal.domain.policy.MealOccupancyPolicy;
import com.amico.amico_backend.meal.domain.policy.MealPollEligibilityPolicy;
import com.amico.amico_backend.meal.domain.policy.MemberSubscriptionPolicy;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.DailyMenuEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.DailyMenuEntryEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.FoodItemEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealComboEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealComboItemEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealParticipationEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealPlanEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.repository.DailyMenuEntryRepository;
import com.amico.amico_backend.meal.infrastructure.persistence.repository.DailyMenuPackageItemRepository;
import com.amico.amico_backend.meal.infrastructure.persistence.repository.DailyMenuRepository;
import com.amico.amico_backend.meal.infrastructure.persistence.repository.MealComboItemRepository;
import com.amico.amico_backend.meal.infrastructure.persistence.repository.MealParticipationRepository;
import com.amico.amico_backend.member.application.service.SpaceMembershipResolver;
import com.amico.amico_backend.member.domain.model.MemberStatus;
import com.amico.amico_backend.member.domain.model.MembershipRole;
import com.amico.amico_backend.member.domain.model.MembershipStatus;
import com.amico.amico_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.amico.amico_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.amico.amico_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.amico.amico_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.amico.amico_backend.space.domain.model.SpaceType;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.amico.amico_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.amico.amico_backend.user.infrastructure.persistence.entity.UserEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MealSharePreviewServiceTest {

    @Mock
    private DailyMenuRepository dailyMenuRepository;

    @Mock
    private DailyMenuEntryRepository dailyMenuEntryRepository;

    @Mock
    private MealComboItemRepository mealComboItemRepository;

    @Mock
    private DailyMenuPackageItemRepository dailyMenuPackageItemRepository;

    @Mock
    private MealParticipationRepository participationRepository;

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private SpaceMembershipRepository spaceMembershipRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberSubscriptionPolicy subscriptionPolicy;

    @Mock
    private MealOccupancyPolicy occupancyPolicy;

    private MealSharePreviewService mealSharePreviewService;

    private UUID spaceId;
    private UUID callerId;
    private LocalDate menuDate;

    @BeforeEach
    void setUp() {
        lenient().when(subscriptionPolicy.canParticipateInPolls(any(), any())).thenReturn(true);
        lenient().when(occupancyPolicy.occupiedMemberIdsForDate(any(), any())).thenReturn(Optional.empty());
        lenient()
                .when(occupancyPolicy.hasOccupancyOnDate(any(), any(), any(), any()))
                .thenReturn(true);
        lenient().when(occupancyPolicy.hasOccupancyOnDate(any(), any(), any())).thenReturn(true);
        mealSharePreviewService = new MealSharePreviewService(
                dailyMenuRepository,
                dailyMenuEntryRepository,
                mealComboItemRepository,
                dailyMenuPackageItemRepository,
                participationRepository,
                spaceRepository,
                new MealAccessService(new SpaceMembershipResolver(spaceMembershipRepository), memberRepository),
                new MealPollEligibilityPolicy(subscriptionPolicy, occupancyPolicy),
                occupancyPolicy);
        spaceId = UUID.randomUUID();
        callerId = UUID.randomUUID();
        menuDate = LocalDate.of(2026, 6, 18);
    }

    @Test
    void getSharePreview_omitsUnpublishedSlotsForFullDayPreview() {
        stubOwnerMembership();
        SpaceEntity space = space();
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(participationRepository.findAllNonStoppedBySpaceId(spaceId)).thenReturn(List.of());

        DailyMenuEntity publishedBreakfast = DailyMenuEntity.builder()
                .space(space)
                .menuDate(menuDate)
                .mealType(MealType.BREAKFAST)
                .status(DailyMenuStatus.PUBLISHED)
                .isDeleted(false)
                .build();
        publishedBreakfast.setId(UUID.randomUUID());

        when(dailyMenuRepository.findBySpaceDateAndType(spaceId, menuDate, MealType.BREAKFAST))
                .thenReturn(Optional.of(publishedBreakfast));
        when(dailyMenuRepository.findBySpaceDateAndType(spaceId, menuDate, MealType.LUNCH))
                .thenReturn(Optional.empty());
        when(dailyMenuRepository.findBySpaceDateAndType(spaceId, menuDate, MealType.DINNER))
                .thenReturn(Optional.empty());
        when(dailyMenuEntryRepository.findByDailyMenuId(publishedBreakfast.getId())).thenReturn(List.of());
        when(dailyMenuRepository.findById(publishedBreakfast.getId())).thenReturn(Optional.of(publishedBreakfast));

        var preview = mealSharePreviewService.getSharePreview(spaceId, callerId, menuDate, null);

        assertThat(preview.getSlots()).hasSize(1);
        assertThat(preview.getSlots().get(0).getMealType()).isEqualTo(MealType.BREAKFAST);
        assertThat(preview.getMessageText()).doesNotContain("(not published)");
    }

    @Test
    void getSharePreview_includesComboDetailAndEligibleCount() {
        stubOwnerMembership();
        SpaceEntity space = space();
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));

        MealComboEntity combo = MealComboEntity.builder().name("Standard Lunch Thali").isActive(true).build();
        combo.setId(UUID.randomUUID());

        DailyMenuEntity published = DailyMenuEntity.builder()
                .space(space)
                .menuDate(menuDate)
                .mealType(MealType.LUNCH)
                .status(DailyMenuStatus.PUBLISHED)
                .notes("Extra salad")
                .isDeleted(false)
                .build();
        published.setId(UUID.randomUUID());

        published.setId(UUID.randomUUID());

        DailyMenuEntryEntity comboEntry = DailyMenuEntryEntity.builder()
                .dailyMenu(published)
                .entryType(DailyMenuEntryType.COMBO)
                .combo(combo)
                .label("Standard Lunch Thali")
                .sortOrder(1)
                .isAvailable(true)
                .build();

        when(dailyMenuRepository.findBySpaceDateAndType(spaceId, menuDate, MealType.LUNCH))
                .thenReturn(Optional.of(published));
        when(dailyMenuEntryRepository.findByDailyMenuId(published.getId())).thenReturn(List.of(comboEntry));
        when(dailyMenuRepository.findById(published.getId())).thenReturn(Optional.of(published));

        FoodItemEntity chapati = FoodItemEntity.builder().name("Chapati").isActive(true).build();
        FoodItemEntity dal = FoodItemEntity.builder().name("Dal Fry").isActive(true).build();
        when(mealComboItemRepository.findByComboIdWithItems(combo.getId()))
                .thenReturn(List.of(
                        MealComboItemEntity.builder().item(chapati).sortOrder(0).build(),
                        MealComboItemEntity.builder().item(dal).sortOrder(1).build()));

        MealParticipationEntity participation = participation(menuDate);
        when(participationRepository.findAllNonStoppedBySpaceId(spaceId)).thenReturn(List.of(participation));

        var preview = mealSharePreviewService.getSharePreview(spaceId, callerId, menuDate, MealType.LUNCH);

        assertThat(preview.getSpaceName()).isEqualTo("Sunrise Mess");
        assertThat(preview.getStatus()).isEqualTo(DailyMenuStatus.PUBLISHED);
        assertThat(preview.getEligibleCount()).isEqualTo(1);
        assertThat(preview.getSlots()).hasSize(1);
        assertThat(preview.getSlots().get(0).getLines().get(0).getDetail()).isEqualTo("Chapati · Dal Fry");
        assertThat(preview.getMessageText()).contains("1. Standard Lunch Thali");
        assertThat(preview.getMessageText()).contains("Chapati, Dal Fry");
        assertThat(preview.getMessageText()).contains("2. Not available for Lunch");
        assertThat(preview.getMessageText()).contains("Eligible participants: 1");
    }

    @Test
    void getSharePreview_marksSingleUnpublishedSlot() {
        stubOwnerMembership();
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space()));
        when(participationRepository.findAllNonStoppedBySpaceId(spaceId)).thenReturn(List.of());
        when(dailyMenuRepository.findBySpaceDateAndType(spaceId, menuDate, MealType.LUNCH))
                .thenReturn(Optional.empty());

        var preview = mealSharePreviewService.getSharePreview(spaceId, callerId, menuDate, MealType.LUNCH);

        assertThat(preview.getSlots()).hasSize(1);
        assertThat(preview.getSlots().get(0).getLines().get(0).getLabel()).isEqualTo("(not published)");
        assertThat(preview.getMessageText()).contains("(not published)");
    }

    private MealParticipationEntity participation(LocalDate date) {
        MemberEntity member = MemberEntity.builder()
                .fullName("Ravi")
                .mobileNumber("9876543210")
                .role(MembershipRole.TENANT)
                .status(MemberStatus.ACTIVE)
                .isActive(true)
                .build();
        member.setId(UUID.randomUUID());

        MealPlanEntity plan = MealPlanEntity.builder()
                .code(MealPlanCode.FULL)
                .name("Full Meals")
                .breakfastIncluded(true)
                .lunchIncluded(true)
                .dinnerIncluded(true)
                .build();

        return MealParticipationEntity.builder()
                .space(space())
                .member(member)
                .mealPlan(plan)
                .status(MealParticipationStatus.ACTIVE)
                .effectiveFrom(date.minusDays(1))
                .build();
    }

    private void stubOwnerMembership() {
        UserEntity user = UserEntity.builder().fullName("Owner").mobileNumber("9000000000").build();
        user.setId(callerId);
        SpaceEntity space = space();
        SpaceMembershipEntity membership = SpaceMembershipEntity.builder()
                .user(user)
                .space(space)
                .role(MembershipRole.OWNER)
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
                .name("Sunrise Mess")
                .type(SpaceType.MESS)
                .isActive(true)
                .build();
        space.setId(spaceId);
        return space;
    }
}
