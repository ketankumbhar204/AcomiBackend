package com.amico.amico_backend.meal.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amico.amico_backend.meal.api.dto.request.CreateMealPlanRequest;
import com.amico.amico_backend.meal.domain.model.MealPlanCode;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealPlanEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.repository.MealPlanRepository;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MealPlanServiceTest {

    @Mock
    private MealPlanRepository mealPlanRepository;

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private SpaceMembershipRepository spaceMembershipRepository;

    @Mock
    private MemberRepository memberRepository;

    private MealPlanService mealPlanService;

    private UUID spaceId;
    private UUID callerId;

    @BeforeEach
    void setUp() {
        mealPlanService = new MealPlanService(
                mealPlanRepository,
                spaceRepository,
                new MealAccessService(new SpaceMembershipResolver(spaceMembershipRepository), memberRepository));
        spaceId = UUID.randomUUID();
        callerId = UUID.randomUUID();
    }

    @Test
    void createCustomPlan_allowsMultipleCustomPlansPerSpace() {
        stubOwnerMembership();
        SpaceEntity space = space();
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        stubAllPresetPlansPresent(space);

        CreateMealPlanRequest request = new CreateMealPlanRequest();
        request.setName("Sunday Special");
        request.setLunchIncluded(true);

        MealPlanEntity saved = MealPlanEntity.builder()
                .space(space)
                .code(MealPlanCode.CUSTOM)
                .name("Sunday Special")
                .lunchIncluded(true)
                .isActive(true)
                .build();
        saved.setId(UUID.randomUUID());
        when(mealPlanRepository.save(any(MealPlanEntity.class))).thenReturn(saved);

        var response = mealPlanService.createCustomPlan(spaceId, callerId, request);

        ArgumentCaptor<MealPlanEntity> captor = ArgumentCaptor.forClass(MealPlanEntity.class);
        verify(mealPlanRepository).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo(MealPlanCode.CUSTOM);
        assertThat(response.getName()).isEqualTo("Sunday Special");
    }

    @Test
    void ensurePresetPlans_backfillsMissingFullPlanWhenSpaceHasOtherPlans() {
        SpaceEntity space = space();
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(mealPlanRepository.existsBySpaceIdAndCode(spaceId, MealPlanCode.NONE)).thenReturn(true);
        when(mealPlanRepository.existsBySpaceIdAndCode(spaceId, MealPlanCode.BREAKFAST)).thenReturn(true);
        when(mealPlanRepository.existsBySpaceIdAndCode(spaceId, MealPlanCode.LUNCH)).thenReturn(true);
        when(mealPlanRepository.existsBySpaceIdAndCode(spaceId, MealPlanCode.DINNER)).thenReturn(true);
        when(mealPlanRepository.existsBySpaceIdAndCode(spaceId, MealPlanCode.FULL)).thenReturn(false);
        when(mealPlanRepository.existsBySpaceIdAndCode(spaceId, MealPlanCode.CUSTOM)).thenReturn(true);

        MealPlanEntity fullPlan = MealPlanEntity.builder()
                .space(space)
                .code(MealPlanCode.FULL)
                .name("Full Meals")
                .breakfastIncluded(true)
                .lunchIncluded(true)
                .dinnerIncluded(true)
                .isActive(true)
                .build();
        fullPlan.setId(UUID.randomUUID());
        when(mealPlanRepository.save(any(MealPlanEntity.class))).thenReturn(fullPlan);

        MealPlanEntity resolved = mealPlanService.ensurePresetPlans(spaceId);

        assertThat(resolved.getCode()).isEqualTo(MealPlanCode.FULL);
        verify(mealPlanRepository).save(any(MealPlanEntity.class));
        verify(mealPlanRepository).flush();
    }

    @Test
    void ensurePresetPlans_backfillsMissingPresetCodesWhenSpaceHasPartialPlans() {
        SpaceEntity space = space();
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(mealPlanRepository.existsBySpaceIdAndCode(spaceId, MealPlanCode.NONE)).thenReturn(true);
        when(mealPlanRepository.existsBySpaceIdAndCode(spaceId, MealPlanCode.BREAKFAST)).thenReturn(false);
        when(mealPlanRepository.existsBySpaceIdAndCode(spaceId, MealPlanCode.LUNCH)).thenReturn(false);
        when(mealPlanRepository.existsBySpaceIdAndCode(spaceId, MealPlanCode.DINNER)).thenReturn(false);
        when(mealPlanRepository.existsBySpaceIdAndCode(spaceId, MealPlanCode.FULL)).thenReturn(false);
        when(mealPlanRepository.existsBySpaceIdAndCode(spaceId, MealPlanCode.CUSTOM)).thenReturn(false);
        MealPlanEntity fullPlan = presetPlan(space, MealPlanCode.FULL, "Full Meals");
        when(mealPlanRepository.save(any(MealPlanEntity.class)))
                .thenAnswer(invocation -> {
                    MealPlanEntity entity = invocation.getArgument(0);
                    if (entity.getCode() == MealPlanCode.FULL) {
                        return fullPlan;
                    }
                    return entity;
                });

        MealPlanEntity resolved = mealPlanService.ensurePresetPlans(spaceId);

        assertThat(resolved.getCode()).isEqualTo(MealPlanCode.FULL);
        verify(mealPlanRepository, times(5)).save(any(MealPlanEntity.class));
        verify(mealPlanRepository).flush();
    }

    private MealPlanEntity presetPlan(SpaceEntity space, MealPlanCode code, String name) {
        return MealPlanEntity.builder()
                .space(space)
                .code(code)
                .name(name)
                .isActive(true)
                .build();
    }

    private void stubAllPresetPlansPresent(SpaceEntity space) {
        when(mealPlanRepository.existsBySpaceIdAndCode(spaceId, MealPlanCode.NONE)).thenReturn(true);
        when(mealPlanRepository.existsBySpaceIdAndCode(spaceId, MealPlanCode.BREAKFAST)).thenReturn(true);
        when(mealPlanRepository.existsBySpaceIdAndCode(spaceId, MealPlanCode.LUNCH)).thenReturn(true);
        when(mealPlanRepository.existsBySpaceIdAndCode(spaceId, MealPlanCode.DINNER)).thenReturn(true);
        when(mealPlanRepository.existsBySpaceIdAndCode(spaceId, MealPlanCode.FULL)).thenReturn(true);
        when(mealPlanRepository.existsBySpaceIdAndCode(spaceId, MealPlanCode.CUSTOM)).thenReturn(true);
        when(mealPlanRepository.findFirstBySpaceIdAndCodeOrderBySortOrderAsc(spaceId, MealPlanCode.FULL))
                .thenReturn(Optional.of(presetPlan(space, MealPlanCode.FULL, "Full Meals")));
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
                .name("Test Mess")
                .type(SpaceType.MESS)
                .isActive(true)
                .build();
        space.setId(spaceId);
        return space;
    }
}
