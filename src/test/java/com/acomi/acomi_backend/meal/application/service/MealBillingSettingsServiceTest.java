package com.acomi.acomi_backend.meal.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.meal.api.dto.request.UpdateMealBillingSettingsRequest;
import com.acomi.acomi_backend.meal.api.dto.response.MealBillingSettingsResponse;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.acomi.acomi_backend.space.domain.model.MealBillingType;
import com.acomi.acomi_backend.space.domain.model.PrepaidBalanceUnit;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MealBillingSettingsServiceTest {

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private MealAccessService mealAccessService;

    @InjectMocks
    private MealBillingSettingsService service;

    private final UUID spaceId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();

    @Test
    void updateSettings_switchesToPrepaidBalanceWithDefaultMealsUnit() {
        SpaceEntity space = messSpace();
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(mealAccessService.requireManageMeals(spaceId, callerId)).thenReturn(new SpaceMembershipEntity());

        UpdateMealBillingSettingsRequest request = new UpdateMealBillingSettingsRequest();
        request.setBillingType(MealBillingType.PREPAID_BALANCE);
        request.setFallbackToPayPerMeal(true);

        MealBillingSettingsResponse response = service.updateSettings(spaceId, callerId, request);

        assertThat(response.getBillingType()).isEqualTo(MealBillingType.PREPAID_BALANCE);
        assertThat(response.getPrepaidBalanceUnit()).isEqualTo(PrepaidBalanceUnit.MEALS);
        assertThat(response.isFallbackToPayPerMeal()).isTrue();
        verify(spaceRepository).save(space);
    }

    @Test
    void updateSettings_payPerMealClearsPrepaidUnit() {
        SpaceEntity space = messSpace();
        space.setMealBillingType(MealBillingType.PREPAID_BALANCE);
        space.setPrepaidBalanceUnit(PrepaidBalanceUnit.CURRENCY);
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(mealAccessService.requireManageMeals(spaceId, callerId)).thenReturn(new SpaceMembershipEntity());

        UpdateMealBillingSettingsRequest request = new UpdateMealBillingSettingsRequest();
        request.setBillingType(MealBillingType.PAY_PER_MEAL);

        MealBillingSettingsResponse response = service.updateSettings(spaceId, callerId, request);

        assertThat(response.getBillingType()).isEqualTo(MealBillingType.PAY_PER_MEAL);
        assertThat(response.getPrepaidBalanceUnit()).isNull();
        assertThat(response.isFallbackToPayPerMeal()).isTrue();
    }

    private SpaceEntity messSpace() {
        return SpaceEntity.builder()
                .owner(UserEntity.builder().build())
                .name("Sunrise Mess")
                .type(SpaceType.MESS)
                .mealBillingType(MealBillingType.PAY_PER_MEAL)
                .prepaidFallbackToPayPerMeal(true)
                .build();
    }
}
