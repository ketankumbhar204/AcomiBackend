package com.countin.countin_backend.space.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.countin.countin_backend.common.exception.BusinessException;
import com.countin.countin_backend.common.exception.ResourceNotFoundException;
import com.countin.countin_backend.inventory.application.service.InventorySeedService;
import com.countin.countin_backend.meal.application.service.MealPlanService;
import com.countin.countin_backend.meal.application.service.MealSpaceSetupService;
import com.countin.countin_backend.member.application.service.MemberMasterService;
import com.countin.countin_backend.member.domain.model.MembershipRole;
import com.countin.countin_backend.member.domain.model.MembershipStatus;
import com.countin.countin_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.countin.countin_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.countin.countin_backend.space.api.dto.request.CreateSpaceRequest;
import com.countin.countin_backend.space.api.dto.request.UpdateSpaceRequest;
import com.countin.countin_backend.space.api.dto.response.DefaultSpaceResponse;
import com.countin.countin_backend.space.api.dto.response.MySpaceResponse;
import com.countin.countin_backend.space.api.dto.response.SetDefaultSpaceResponse;
import com.countin.countin_backend.space.api.dto.response.SpaceDetailsResponse;
import com.countin.countin_backend.space.api.dto.response.SpaceResponse;
import com.countin.countin_backend.space.domain.model.SpaceType;
import com.countin.countin_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.countin.countin_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.countin.countin_backend.user.infrastructure.persistence.entity.UserEntity;
import com.countin.countin_backend.user.infrastructure.persistence.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class SpaceServiceTest {

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
    private UUID otherUserId;
    private UUID spaceId;
    private UserEntity owner;
    private SpaceEntity space;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        spaceId = UUID.randomUUID();

        owner = UserEntity.builder()
                .mobileNumber("9876543210")
                .fullName("Ketan")
                .build();
        owner.setId(ownerId);
        owner.setCreatedAt(LocalDateTime.now());
        owner.setUpdatedAt(LocalDateTime.now());

        space = SpaceEntity.builder()
                .owner(owner)
                .name("Sunrise PG")
                .type(SpaceType.PG)
                .address("Pune")
                .contactNumber("9876543210")
                .isActive(true)
                .build();
        space.setId(spaceId);
        space.setCreatedAt(LocalDateTime.now());
        space.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    void createSpace_withRentalType_persistsRentalSpace() {
        CreateSpaceRequest request = mock(CreateSpaceRequest.class);
        when(request.getName()).thenReturn("Sunrise Apartments");
        when(request.getType()).thenReturn(SpaceType.RENTAL);
        when(request.getOwnerId()).thenReturn(ownerId);

        when(userRepository.findByIdAndIsActiveTrue(ownerId)).thenReturn(Optional.of(owner));
        when(spaceRepository.save(any(SpaceEntity.class))).thenAnswer(invocation -> {
            SpaceEntity saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(LocalDateTime.now());
            saved.setUpdatedAt(LocalDateTime.now());
            return saved;
        });

        SpaceResponse response = spaceService.createSpace(request);

        ArgumentCaptor<SpaceEntity> spaceCaptor = ArgumentCaptor.forClass(SpaceEntity.class);
        verify(spaceRepository).save(spaceCaptor.capture());

        assertThat(spaceCaptor.getValue().getType()).isEqualTo(SpaceType.RENTAL);
        assertThat(spaceCaptor.getValue().getName()).isEqualTo("Sunrise Apartments");
        assertThat(response.getType()).isEqualTo(SpaceType.RENTAL);
        assertThat(response.getName()).isEqualTo("Sunrise Apartments");
        verify(inventorySeedService).seedDefaults(any(SpaceEntity.class));
    }

    @Test
    void getSpaceById_whenSpaceExists_returnsDetails() {
        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));

        SpaceDetailsResponse response = spaceService.getSpaceById(spaceId);

        assertThat(response.getId()).isEqualTo(spaceId);
        assertThat(response.getName()).isEqualTo("Sunrise PG");
        assertThat(response.getType()).isEqualTo(SpaceType.PG);
        assertThat(response.getOwnerId()).isEqualTo(ownerId);
        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getUpdatedAt()).isNotNull();
    }

    @Test
    void getSpaceById_whenSpaceNotFound_throwsResourceNotFoundException() {
        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> spaceService.getSpaceById(spaceId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Space not found");
    }

    @Test
    void updateSpace_whenCallerIsOwner_updatesAndReturnsDetails() {
        UpdateSpaceRequest request = mock(UpdateSpaceRequest.class);
        when(request.getName()).thenReturn("Sunrise PG Updated");
        when(request.getAddress()).thenReturn("Mumbai");
        when(request.getContactNumber()).thenReturn("9123456789");
        when(request.getFoodIncludedInRent()).thenReturn(false);
        when(request.getDefaultFoodCharge()).thenReturn(new java.math.BigDecimal("2000"));

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        when(spaceRepository.save(any(SpaceEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SpaceDetailsResponse response = spaceService.updateSpace(spaceId, ownerId, request);

        assertThat(response.getName()).isEqualTo("Sunrise PG Updated");
        assertThat(response.getAddress()).isEqualTo("Mumbai");
        assertThat(response.getContactNumber()).isEqualTo("9123456789");
        assertThat(response.isFoodIncludedInRent()).isFalse();
        assertThat(response.getDefaultFoodCharge()).isEqualByComparingTo("2000");
        assertThat(response.getType()).isEqualTo(SpaceType.PG);
        assertThat(response.getOwnerId()).isEqualTo(ownerId);

        ArgumentCaptor<SpaceEntity> captor = ArgumentCaptor.forClass(SpaceEntity.class);
        verify(spaceRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Sunrise PG Updated");
    }

    @Test
    void updateSpace_whenCallerIsNotOwner_throwsForbidden() {
        UpdateSpaceRequest request = mock(UpdateSpaceRequest.class);
        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));

        assertThatThrownBy(() -> spaceService.updateSpace(spaceId, otherUserId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only OWNER or MANAGER")
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(spaceRepository, never()).save(any());
    }

    @Test
    void updateSpace_whenSpaceNotFound_throwsResourceNotFoundException() {
        UpdateSpaceRequest request = mock(UpdateSpaceRequest.class);
        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> spaceService.updateSpace(spaceId, ownerId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deactivateSpace_whenCallerIsOwner_setsInactive() {
        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        when(spaceRepository.save(any(SpaceEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        spaceService.deactivateSpace(spaceId, ownerId);

        ArgumentCaptor<SpaceEntity> captor = ArgumentCaptor.forClass(SpaceEntity.class);
        verify(spaceRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
    }

    @Test
    void deactivateSpace_whenCallerIsNotOwner_throwsForbidden() {
        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));

        assertThatThrownBy(() -> spaceService.deactivateSpace(spaceId, otherUserId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only the space owner")
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(spaceRepository, never()).save(any());
    }

    @Test
    void deactivateSpace_whenSpaceNotFound_throwsResourceNotFoundException() {
        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> spaceService.deactivateSpace(spaceId, ownerId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getUserSpaces_returnsOnlyActiveMembershipsAndActiveSpaces() {
        SpaceMembershipEntity activeMembership = SpaceMembershipEntity.builder()
                .user(owner)
                .space(space)
                .role(MembershipRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();

        SpaceEntity inactiveSpace = SpaceEntity.builder()
                .owner(owner)
                .name("Closed PG")
                .type(SpaceType.PG)
                .isActive(false)
                .build();
        inactiveSpace.setId(UUID.randomUUID());

        SpaceMembershipEntity inactiveSpaceMembership = SpaceMembershipEntity.builder()
                .user(owner)
                .space(inactiveSpace)
                .role(MembershipRole.TENANT)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();

        SpaceMembershipEntity inactiveMembership = SpaceMembershipEntity.builder()
                .user(owner)
                .space(space)
                .role(MembershipRole.TENANT)
                .status(MembershipStatus.INACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();

        when(spaceMembershipRepository.findByUserIdWithSpace(ownerId))
                .thenReturn(List.of(activeMembership, inactiveSpaceMembership, inactiveMembership));

        var responses = spaceService.getUserSpaces(ownerId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getSpaceId()).isEqualTo(spaceId);
        assertThat(responses.get(0).getMembershipRole()).isEqualTo(MembershipRole.OWNER);
        assertThat(responses.get(0).getJoinedAt()).isNotNull();
    }

    @Test
    void getMySpaces_returnsSpacesFromRepository() {
        SpaceMembershipEntity membership = SpaceMembershipEntity.builder()
                .user(owner)
                .space(space)
                .role(MembershipRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .isDefault(true)
                .joinedAt(LocalDateTime.now())
                .build();

        when(spaceMembershipRepository.findUserSpaces(ownerId)).thenReturn(List.of(membership));

        List<MySpaceResponse> responses = spaceService.getMySpaces(ownerId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getSpaceId()).isEqualTo(spaceId);
        assertThat(responses.get(0).isDefault()).isTrue();
        assertThat(responses.get(0).getMembershipRole()).isEqualTo(MembershipRole.OWNER);
    }

    @Test
    void searchMySpaces_returnsMatchingSpaces() {
        SpaceMembershipEntity membership = SpaceMembershipEntity.builder()
                .user(owner)
                .space(space)
                .role(MembershipRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();

        when(spaceMembershipRepository.searchUserSpaces(ownerId, "sun"))
                .thenReturn(List.of(membership));

        List<MySpaceResponse> responses = spaceService.searchMySpaces(ownerId, "sun");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getSpaceName()).isEqualTo("Sunrise PG");
    }

    @Test
    void setDefaultSpace_whenMembershipExists_clearsPreviousAndSetsNew() {
        SpaceMembershipEntity membership = SpaceMembershipEntity.builder()
                .user(owner)
                .space(space)
                .role(MembershipRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .isDefault(false)
                .joinedAt(LocalDateTime.now())
                .build();

        when(spaceMembershipRepository.findMembershipByUserAndSpace(ownerId, spaceId))
                .thenReturn(Optional.of(membership));
        when(spaceMembershipRepository.save(any(SpaceMembershipEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SetDefaultSpaceResponse response = spaceService.setDefaultSpace(ownerId, spaceId);

        verify(spaceMembershipRepository).clearDefaultSpaceForUser(ownerId);
        verify(spaceMembershipRepository).save(membership);
        assertThat(membership.isDefault()).isTrue();
        assertThat(response.getSpaceId()).isEqualTo(spaceId);
        assertThat(response.isDefault()).isTrue();
    }

    @Test
    void setDefaultSpace_whenMembershipNotFound_throwsResourceNotFoundException() {
        when(spaceMembershipRepository.findMembershipByUserAndSpace(ownerId, spaceId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> spaceService.setDefaultSpace(ownerId, spaceId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(spaceMembershipRepository, never()).clearDefaultSpaceForUser(ownerId);
    }

    @Test
    void getDefaultSpace_whenDefaultExists_returnsDefaultSpace() {
        SpaceMembershipEntity membership = SpaceMembershipEntity.builder()
                .user(owner)
                .space(space)
                .role(MembershipRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .isDefault(true)
                .joinedAt(LocalDateTime.now())
                .build();

        when(spaceMembershipRepository.findDefaultSpace(ownerId)).thenReturn(Optional.of(membership));

        DefaultSpaceResponse response = spaceService.getDefaultSpace(ownerId);

        assertThat(response.getSpaceId()).isEqualTo(spaceId);
        assertThat(response.getSpaceName()).isEqualTo("Sunrise PG");
        assertThat(response.getSpaceType()).isEqualTo(SpaceType.PG);
    }

    @Test
    void getDefaultSpace_whenNoDefault_throwsResourceNotFoundException() {
        when(spaceMembershipRepository.findDefaultSpace(ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> spaceService.getDefaultSpace(ownerId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
