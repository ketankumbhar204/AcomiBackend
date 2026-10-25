package com.acomi.acomi_backend.meal.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.FoodItemRepository;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.MealComboItemRepository;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.MealComboRepository;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MealSpaceSetupServiceTest {

    @Mock
    private MealComboRepository mealComboRepository;

    @Mock
    private MealComboItemRepository mealComboItemRepository;

    @Mock
    private FoodItemRepository foodItemRepository;

    private MealSpaceSetupService mealSpaceSetupService;
    private SpaceEntity space;

    @BeforeEach
    void setUp() {
        mealSpaceSetupService = new MealSpaceSetupService(
                mealComboRepository, mealComboItemRepository, foodItemRepository);
        UserEntity owner = UserEntity.builder().fullName("Owner").mobileNumber("9000000001").build();
        owner.setId(UUID.randomUUID());
        space = SpaceEntity.builder()
                .owner(owner)
                .name("Sunrise PG")
                .type(SpaceType.PG)
                .isActive(true)
                .build();
        space.setId(UUID.randomUUID());
    }

    @Test
    void ensureSampleCombos_skipsWhenOwnerAlreadyHasTheSample() {
        when(foodItemRepository.findGlobalByName("Chapati"))
                .thenReturn(java.util.Optional.of(
                        com.acomi.acomi_backend.meal.infrastructure.persistence.entity.FoodItemEntity.builder()
                                .name("Chapati")
                                .build()));
        when(mealComboRepository.existsListedSample(space.getOwner().getId(), "standard-lunch-thali"))
                .thenReturn(true);
        when(mealComboRepository.existsListedSample(space.getOwner().getId(), "dal-rice-combo"))
                .thenReturn(true);
        when(mealComboRepository.existsListedSample(space.getOwner().getId(), "chicken-thali"))
                .thenReturn(true);
        when(mealComboRepository.existsListedSample(space.getOwner().getId(), "egg-combo"))
                .thenReturn(true);

        mealSpaceSetupService.ensureSampleCombos(space);

        verify(mealComboRepository, never()).save(any());
    }
}
