package com.amico.amico_backend.meal.application.service;

import com.amico.amico_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.amico.amico_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.amico.amico_backend.user.infrastructure.persistence.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MealOccupancyBridgeService {

    private final MealParticipationService mealParticipationService;

    @Transactional
    public void onOccupancyActivated(OccupancyEntity occupancy, UserEntity actor, boolean createMealParticipation) {
        if (!createMealParticipation) {
            return;
        }
        if (!occupancy.isFoodEnabled() && !occupancy.isFoodIncludedInRent()) {
            return;
        }
        mealParticipationService.createFromOccupancy(occupancy, actor);
    }

    @Transactional
    public void onVacate(MemberEntity member, UserEntity actor) {
        mealParticipationService.stopOnVacate(member, actor);
    }
}
