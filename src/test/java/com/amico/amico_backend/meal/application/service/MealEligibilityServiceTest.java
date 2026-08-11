package com.amico.amico_backend.meal.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.amico.amico_backend.meal.domain.model.MealParticipationStatus;
import com.amico.amico_backend.meal.domain.model.MealPlanCode;
import com.amico.amico_backend.meal.domain.model.MealType;
import com.amico.amico_backend.meal.domain.policy.MealOccupancyPolicy;
import com.amico.amico_backend.meal.domain.policy.MealPollEligibilityPolicy;
import com.amico.amico_backend.meal.domain.policy.MemberSubscriptionPolicy;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealParticipationEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealPlanEntity;
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
class MealEligibilityServiceTest {

    @Mock
    private MealParticipationRepository participationRepository;

    @Mock
    private DailyMenuService dailyMenuService;

    @Mock
    private SpaceMembershipRepository spaceMembershipRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberSubscriptionPolicy subscriptionPolicy;

    @Mock
    private MealOccupancyPolicy occupancyPolicy;

    @Mock
    private SpaceRepository spaceRepository;

    private MealEligibilityService mealEligibilityService;

    private UUID spaceId;
    private UUID callerId;
    private SpaceEntity messSpace;

    @BeforeEach
    void setUp() {
        when(subscriptionPolicy.canParticipateInPolls(any(), any())).thenReturn(true);
        lenient().when(occupancyPolicy.occupiedMemberIdsForDate(any(), any())).thenReturn(Optional.empty());
        lenient()
                .when(occupancyPolicy.hasOccupancyOnDate(any(), any(), any(), any()))
                .thenReturn(true);
        lenient().when(occupancyPolicy.hasOccupancyOnDate(any(), any(), any())).thenReturn(true);
        mealEligibilityService = new MealEligibilityService(
                participationRepository,
                dailyMenuService,
                new MealAccessService(new SpaceMembershipResolver(spaceMembershipRepository), memberRepository),
                new MealPollEligibilityPolicy(subscriptionPolicy, occupancyPolicy),
                occupancyPolicy,
                spaceRepository);
        spaceId = UUID.randomUUID();
        callerId = UUID.randomUUID();
        messSpace = SpaceEntity.builder()
                .name("Mess")
                .type(SpaceType.MESS)
                .isActive(true)
                .build();
        messSpace.setId(spaceId);
    }

    @Test
    void listEligibleParticipants_returnsFrontendContractFields() {
        stubOwnerMembership();
        LocalDate date = LocalDate.of(2026, 7, 15);
        MemberEntity member = MemberEntity.builder()
                .fullName("Ravi Kumar")
                .mobileNumber("9876543210")
                .role(MembershipRole.TENANT)
                .status(MemberStatus.ACTIVE)
                .isActive(true)
                .build();
        member.setId(UUID.randomUUID());

        MealPlanEntity fullPlan = MealPlanEntity.builder()
                .code(MealPlanCode.FULL)
                .name("Full Meals")
                .breakfastIncluded(true)
                .lunchIncluded(true)
                .dinnerIncluded(true)
                .build();

        MealParticipationEntity participation = MealParticipationEntity.builder()
                .space(messSpace)
                .member(member)
                .mealPlan(fullPlan)
                .status(MealParticipationStatus.ACTIVE)
                .effectiveFrom(date.minusDays(1))
                .build();

        when(participationRepository.findAllActiveBySpaceId(spaceId)).thenReturn(List.of(participation));

        var participants = mealEligibilityService.listEligibleParticipants(spaceId, callerId, date, MealType.LUNCH);

        assertThat(participants).hasSize(1);
        assertThat(participants.get(0).getMemberId()).isEqualTo(member.getId());
        assertThat(participants.get(0).getMemberName()).isEqualTo("Ravi Kumar");
        assertThat(participants.get(0).getMobileNumber()).isEqualTo("9876543210");
        assertThat(participants.get(0).getMealPlanCode()).isEqualTo(MealPlanCode.FULL);
        assertThat(participants.get(0).getMealPlanName()).isEqualTo("Full Meals");
    }

    @Test
    void getSummary_includesPausedCountAndByPlanBreakdown() {
        stubOwnerMembership();
        LocalDate date = LocalDate.of(2026, 7, 15);

        MealParticipationEntity active = participation(
                "Active Member", MealPlanCode.FULL, "Full Meals", MealParticipationStatus.ACTIVE, date);
        MealParticipationEntity paused = participation(
                "Paused Member", MealPlanCode.LUNCH, "Lunch Only", MealParticipationStatus.PAUSED, date);

        when(participationRepository.findAllNonStoppedBySpaceId(spaceId)).thenReturn(List.of(active, paused));
        when(dailyMenuService.isPublished(spaceId, date, MealType.LUNCH)).thenReturn(true);
        when(dailyMenuService.isPublished(spaceId, date, MealType.BREAKFAST)).thenReturn(false);
        when(dailyMenuService.isPublished(spaceId, date, MealType.DINNER)).thenReturn(false);

        var summary = mealEligibilityService.getSummary(spaceId, callerId, date);
        var lunchSlot = summary.getSlots().stream()
                .filter(slot -> slot.getMealType() == MealType.LUNCH)
                .findFirst()
                .orElseThrow();

        assertThat(lunchSlot.getEligibleCount()).isEqualTo(1);
        assertThat(lunchSlot.getPausedCount()).isEqualTo(1);
        assertThat(lunchSlot.isPublished()).isTrue();
        assertThat(lunchSlot.getByPlan()).hasSize(1);
        assertThat(lunchSlot.getByPlan().get(0).getMealPlanCode()).isEqualTo(MealPlanCode.FULL);
        assertThat(lunchSlot.getByPlan().get(0).getCount()).isEqualTo(1);
        assertThat(summary.getDistinctEligibleMemberCount()).isEqualTo(1);
    }

    @Test
    void getSummary_distinctEligibleCount_doesNotSumAcrossMealSlots() {
        stubOwnerMembership();
        LocalDate date = LocalDate.of(2026, 7, 15);

        MealParticipationEntity memberOne = participation(
                "Member One", MealPlanCode.FULL, "Full Meals", MealParticipationStatus.ACTIVE, date);
        MealParticipationEntity memberTwo = participation(
                "Member Two", MealPlanCode.FULL, "Full Meals", MealParticipationStatus.ACTIVE, date);
        MealParticipationEntity memberThree = participation(
                "Member Three", MealPlanCode.FULL, "Full Meals", MealParticipationStatus.ACTIVE, date);

        when(participationRepository.findAllNonStoppedBySpaceId(spaceId))
                .thenReturn(List.of(memberOne, memberTwo, memberThree));
        when(dailyMenuService.isPublished(spaceId, date, MealType.LUNCH)).thenReturn(false);
        when(dailyMenuService.isPublished(spaceId, date, MealType.BREAKFAST)).thenReturn(false);
        when(dailyMenuService.isPublished(spaceId, date, MealType.DINNER)).thenReturn(false);

        var summary = mealEligibilityService.getSummary(spaceId, callerId, date);

        assertThat(summary.getDistinctEligibleMemberCount()).isEqualTo(3);
        assertThat(summary.getSlots()).allMatch(slot -> slot.getEligibleCount() == 3);
        int summedSlotCounts = summary.getSlots().stream()
                .mapToInt(slot -> slot.getEligibleCount())
                .sum();
        assertThat(summedSlotCounts).isEqualTo(9);
        assertThat(summary.getDistinctEligibleMemberCount()).isNotEqualTo(summedSlotCounts);
    }

    private MealParticipationEntity participation(
            String name,
            MealPlanCode code,
            String planName,
            MealParticipationStatus status,
            LocalDate date) {
        MemberEntity member = MemberEntity.builder()
                .fullName(name)
                .mobileNumber("9000000000")
                .role(MembershipRole.TENANT)
                .status(MemberStatus.ACTIVE)
                .isActive(true)
                .build();
        member.setId(UUID.randomUUID());

        MealPlanEntity plan = MealPlanEntity.builder()
                .code(code)
                .name(planName)
                .breakfastIncluded(code == MealPlanCode.FULL || code == MealPlanCode.BREAKFAST)
                .lunchIncluded(code == MealPlanCode.FULL || code == MealPlanCode.LUNCH)
                .dinnerIncluded(code == MealPlanCode.FULL || code == MealPlanCode.DINNER)
                .build();

        return MealParticipationEntity.builder()
                .space(messSpace)
                .member(member)
                .mealPlan(plan)
                .status(status)
                .effectiveFrom(date.minusDays(1))
                .build();
    }

    private void stubOwnerMembership() {
        UserEntity user = UserEntity.builder().fullName("Owner").mobileNumber("9000000000").build();
        user.setId(callerId);
        SpaceEntity space = SpaceEntity.builder()
                .owner(user)
                .name("Mess")
                .type(SpaceType.MESS)
                .isActive(true)
                .build();
        space.setId(spaceId);
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        SpaceMembershipEntity membership = SpaceMembershipEntity.builder()
                .user(user)
                .space(space)
                .role(MembershipRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .build();
        when(spaceMembershipRepository.findMembershipByUserAndSpace(callerId, spaceId))
                .thenReturn(Optional.of(membership));
    }
}
