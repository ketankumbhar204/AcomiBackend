package com.acomi.acomi_backend.meal.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import org.junit.jupiter.api.Test;

class MealPricingPolicyTest {

    @Test
    void messUsesSeparateMealBilling() {
        SpaceEntity space = SpaceEntity.builder().type(SpaceType.MESS).foodIncludedInRent(false).build();
        assertThat(MealPricingPolicy.usesSeparateMealBilling(space)).isTrue();
        assertThat(MealPricingPolicy.requiresMealPrices(space)).isTrue();
    }

    @Test
    void pgUsesHeadcountOnlyPolls() {
        SpaceEntity space = SpaceEntity.builder().type(SpaceType.PG).foodIncludedInRent(true).build();
        assertThat(MealPricingPolicy.usesSeparateMealBilling(space)).isFalse();
        assertThat(MealPricingPolicy.requiresMealPrices(space)).isFalse();
    }

    @Test
    void hostelAndCoLivingDoNotBillMealsSeparately() {
        assertThat(MealPricingPolicy.usesSeparateMealBilling(
                        SpaceEntity.builder().type(SpaceType.HOSTEL).build()))
                .isFalse();
        assertThat(MealPricingPolicy.usesSeparateMealBilling(
                        SpaceEntity.builder().type(SpaceType.CO_LIVING).build()))
                .isFalse();
    }
}
