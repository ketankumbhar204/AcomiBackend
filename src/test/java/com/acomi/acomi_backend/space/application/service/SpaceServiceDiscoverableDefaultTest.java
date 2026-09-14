package com.acomi.acomi_backend.space.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.inventory.application.service.InventorySeedService;
import com.acomi.acomi_backend.meal.application.service.MealPlanService;
import com.acomi.acomi_backend.meal.application.service.MealSpaceSetupService;
import com.acomi.acomi_backend.member.application.service.MemberMasterService;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.space.api.dto.request.CreateSpaceRequest;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpaceServiceDiscoverableDefaultTest {

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SpaceMembershipRepository spaceMembershipRepository;

    @Mock
    private MemberMasterService memberMasterService;

    @Mock
    private MealSpaceSetupService mealSpaceSetupService;

    @Mock
    private MealPlanService mealPlanService;

    @Mock
    private InventorySeedService inventorySeedService;

    @Mock
    private SpaceAmenityService spaceAmenityService;

    @InjectMocks
    private SpaceService spaceService;

    private UUID ownerId;
    private UserEntity owner;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        owner = UserEntity.builder().mobileNumber("9876543210").fullName("Owner").build();
        owner.setId(ownerId);
    }

    @Test
    void createSpace_nullDiscoverable_defaultsTrue() {
        CreateSpaceRequest request = new CreateSpaceRequest();
        request.setName("Owner PG");
        request.setType(SpaceType.PG);
        request.setOwnerId(ownerId);
        request.setDiscoverable(null);

        when(userRepository.findByIdAndIsActiveTrue(ownerId)).thenReturn(Optional.of(owner));
        when(spaceRepository.save(any(SpaceEntity.class))).thenAnswer(inv -> {
            SpaceEntity saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(LocalDateTime.now());
            return saved;
        });
        when(spaceMembershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        spaceService.createSpace(request);

        ArgumentCaptor<SpaceEntity> captor = ArgumentCaptor.forClass(SpaceEntity.class);
        org.mockito.Mockito.verify(spaceRepository).save(captor.capture());
        assertThat(captor.getValue().isDiscoverable()).isTrue();
    }

    @Test
    void createSpace_explicitFalse_persistsFalse() {
        CreateSpaceRequest request = new CreateSpaceRequest();
        request.setName("Converted PG");
        request.setType(SpaceType.PG);
        request.setOwnerId(ownerId);
        request.setDiscoverable(false);

        when(userRepository.findByIdAndIsActiveTrue(ownerId)).thenReturn(Optional.of(owner));
        when(spaceRepository.save(any(SpaceEntity.class))).thenAnswer(inv -> {
            SpaceEntity saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(LocalDateTime.now());
            return saved;
        });
        when(spaceMembershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        spaceService.createSpace(request);

        ArgumentCaptor<SpaceEntity> captor = ArgumentCaptor.forClass(SpaceEntity.class);
        org.mockito.Mockito.verify(spaceRepository).save(captor.capture());
        assertThat(captor.getValue().isDiscoverable()).isFalse();
    }
}
