package com.amico.amico_backend.meal.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.amico.amico_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.amico.amico_backend.space.domain.model.MealBillingType;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.amico.amico_backend.user.infrastructure.persistence.entity.UserEntity;
import org.junit.jupiter.api.Test;

class MealBillingResolverTest {

    private final MealBillingResolver resolver = new MealBillingResolver();

    @Test
    void resolve_usesMemberOverrideWhenPresent() {
        SpaceEntity space = space(MealBillingType.PAY_PER_MEAL);
        MemberEntity member = member(MealBillingType.PREPAID_BALANCE, space);

        assertThat(resolver.resolve(space, member)).isEqualTo(MealBillingType.PREPAID_BALANCE);
    }

    @Test
    void resolve_fallsBackToSpaceDefault() {
        SpaceEntity space = space(MealBillingType.PREPAID_BALANCE);
        MemberEntity member = member(null, space);

        assertThat(resolver.resolve(space, member)).isEqualTo(MealBillingType.PREPAID_BALANCE);
    }

    @Test
    void resolve_defaultsToPayPerMeal() {
        SpaceEntity space = space(null);
        MemberEntity member = member(null, space);

        assertThat(resolver.resolve(space, member)).isEqualTo(MealBillingType.PAY_PER_MEAL);
    }

    private SpaceEntity space(MealBillingType billingType) {
        return SpaceEntity.builder()
                .owner(UserEntity.builder().build())
                .name("Test Mess")
                .mealBillingType(billingType != null ? billingType : MealBillingType.PAY_PER_MEAL)
                .build();
    }

    private MemberEntity member(MealBillingType billingType, SpaceEntity space) {
        return MemberEntity.builder()
                .space(space)
                .fullName("Member")
                .mobileNumber("9876543210")
                .mealBillingType(billingType)
                .build();
    }
}
