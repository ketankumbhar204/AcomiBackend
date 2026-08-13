package com.acomi.acomi_backend.meal.application.support;

import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.space.domain.model.MealBillingType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import org.springframework.stereotype.Component;

@Component
public class MealBillingResolver {

    public MealBillingType resolve(SpaceEntity space, MemberEntity member) {
        if (member != null && member.getMealBillingType() != null) {
            return member.getMealBillingType();
        }
        if (space != null && space.getMealBillingType() != null) {
            return space.getMealBillingType();
        }
        return MealBillingType.PAY_PER_MEAL;
    }

    public boolean isPrepaid(SpaceEntity space, MemberEntity member) {
        return resolve(space, member) == MealBillingType.PREPAID_BALANCE;
    }
}
