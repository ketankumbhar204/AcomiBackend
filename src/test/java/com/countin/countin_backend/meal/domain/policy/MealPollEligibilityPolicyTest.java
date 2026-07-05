package com.countin.countin_backend.meal.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.countin.countin_backend.meal.domain.model.MealParticipationStatus;
import com.countin.countin_backend.meal.domain.model.MealPlanCode;
import com.countin.countin_backend.meal.domain.model.MealType;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.MealParticipationEntity;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.MealPlanEntity;
import com.countin.countin_backend.member.domain.model.MemberStatus;
import com.countin.countin_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.countin.countin_backend.space.domain.model.SpaceType;
import com.countin.countin_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MealPollEligibilityPolicyTest {

    @Mock
    private MemberSubscriptionPolicy subscriptionPolicy;

    @Mock
    private MealOccupancyPolicy occupancyPolicy;

    private MealPollEligibilityPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new MealPollEligibilityPolicy(subscriptionPolicy, occupancyPolicy);
        lenient().when(subscriptionPolicy.canParticipateInPolls(any(), any())).thenReturn(true);
    }

    @Test
    void isPollEligible_falseForPgTenantWithoutOccupancy() {
        LocalDate date = LocalDate.of(2026, 7, 4);
        MealParticipationEntity participation = activeParticipation(SpaceType.PG);
        when(occupancyPolicy.hasOccupancyOnDate(
                        participation.getSpace(), participation.getMember().getId(), date, null))
                .thenReturn(false);

        assertThat(policy.isPollEligible(participation, date, MealType.LUNCH)).isFalse();
    }

    @Test
    void isPollEligible_trueForPgTenantWithOccupancy() {
        LocalDate date = LocalDate.of(2026, 7, 4);
        MealParticipationEntity participation = activeParticipation(SpaceType.PG);
        when(occupancyPolicy.hasOccupancyOnDate(
                        participation.getSpace(), participation.getMember().getId(), date, null))
                .thenReturn(true);

        assertThat(policy.isPollEligible(participation, date, MealType.LUNCH)).isTrue();
    }

    @Test
    void isPollEligible_trueForMessCustomerWithoutOccupancyCheck() {
        LocalDate date = LocalDate.of(2026, 7, 4);
        MealParticipationEntity participation = activeParticipation(SpaceType.MESS);
        when(occupancyPolicy.hasOccupancyOnDate(
                        participation.getSpace(), participation.getMember().getId(), date, null))
                .thenReturn(true);

        assertThat(policy.isPollEligible(participation, date, MealType.LUNCH)).isTrue();
    }

    @Test
    void isPollEligible_usesPreloadedOccupiedMemberIds() {
        LocalDate date = LocalDate.of(2026, 7, 4);
        MealParticipationEntity participation = activeParticipation(SpaceType.PG);
        UUID memberId = participation.getMember().getId();
        when(occupancyPolicy.hasOccupancyOnDate(participation.getSpace(), memberId, date, Set.of()))
                .thenReturn(false);

        assertThat(policy.isPollEligible(participation, date, MealType.LUNCH, Set.of())).isFalse();
    }

    private static MealParticipationEntity activeParticipation(SpaceType spaceType) {
        SpaceEntity space = SpaceEntity.builder().type(spaceType).name("Space").build();
        space.setId(UUID.randomUUID());
        MemberEntity member = MemberEntity.builder()
                .status(MemberStatus.ACTIVE)
                .isActive(true)
                .build();
        member.setId(UUID.randomUUID());
        MealPlanEntity plan = MealPlanEntity.builder()
                .code(MealPlanCode.FULL)
                .breakfastIncluded(true)
                .lunchIncluded(true)
                .dinnerIncluded(true)
                .build();
        return MealParticipationEntity.builder()
                .space(space)
                .member(member)
                .mealPlan(plan)
                .status(MealParticipationStatus.ACTIVE)
                .effectiveFrom(LocalDate.now().minusDays(1))
                .build();
    }
}
