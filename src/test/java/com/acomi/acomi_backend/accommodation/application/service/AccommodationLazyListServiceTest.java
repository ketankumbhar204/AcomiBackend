package com.acomi.acomi_backend.accommodation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.accommodation.api.dto.response.FloorListItemResponse;
import com.acomi.acomi_backend.accommodation.domain.model.AccommodationStatus;
import com.acomi.acomi_backend.accommodation.domain.policy.AccommodationProfileResolver;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.AccommodationLazyListRepository;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.BuildingRepository;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.FloorRepository;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.RoomRepository;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.UnitRepository;
import com.acomi.acomi_backend.common.web.PagedResponse;
import com.acomi.acomi_backend.member.application.service.SpaceMembershipResolver;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.domain.model.MembershipStatus;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AccommodationLazyListServiceTest {

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private SpaceMembershipRepository spaceMembershipRepository;

    @Spy
    private AccommodationProfileResolver profileResolver = new AccommodationProfileResolver();

    @Mock
    private AccommodationLazyListRepository lazyListRepository;

    @Mock
    private BuildingRepository buildingRepository;

    @Mock
    private FloorRepository floorRepository;

    @Mock
    private UnitRepository unitRepository;

    @Mock
    private RoomRepository roomRepository;

    private AccommodationAccessService accessService;
    private AccommodationLazyListService lazyListService;

    private UUID spaceId;
    private UUID buildingId;
    private UUID callerId;

    @BeforeEach
    void setUp() {
        SpaceMembershipResolver membershipResolver = new SpaceMembershipResolver(spaceMembershipRepository);
        accessService = new AccommodationAccessService(spaceRepository, membershipResolver, profileResolver);
        lazyListService = new AccommodationLazyListService(
                accessService,
                lazyListRepository,
                buildingRepository,
                floorRepository,
                unitRepository,
                roomRepository);
        spaceId = UUID.randomUUID();
        buildingId = UUID.randomUUID();
        callerId = UUID.randomUUID();
    }

    @Test
    void listFloorsByBuilding_returnsPagedSummaryItems() {
        SpaceEntity space = SpaceEntity.builder().name("PG").type(SpaceType.PG).isActive(true).build();
        space.setId(spaceId);

        BuildingEntity building = BuildingEntity.builder().space(space).name("Block A").code("A").build();
        building.setId(buildingId);

        UUID floorId = UUID.randomUUID();
        FloorListItemResponse item = new FloorListItemResponse(floorId, "Floor 1", 20, 60, 60, 0);
        Pageable pageable = PageRequest.of(0, 20);

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        UserEntity user = UserEntity.builder().fullName("Caller").mobileNumber("9000000006").build();
        user.setId(callerId);
        SpaceMembershipEntity membership = SpaceMembershipEntity.builder()
                .user(user)
                .space(space)
                .role(MembershipRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .build();
        when(spaceMembershipRepository.findMembershipByUserAndSpace(callerId, spaceId))
                .thenReturn(Optional.of(membership));
        when(buildingRepository.findActiveByIdAndSpaceId(buildingId, spaceId)).thenReturn(Optional.of(building));
        when(lazyListRepository.findFloorListItemsByBuildingId(
                        eq(buildingId),
                        eq(null),
                        eq(AccommodationStatus.AVAILABLE),
                        eq(AccommodationStatus.OCCUPIED),
                        eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(item), pageable, 1));

        PagedResponse<FloorListItemResponse> response =
                lazyListService.listFloorsByBuilding(spaceId, buildingId, callerId, null, pageable);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getRoomCount()).isEqualTo(20);
        assertThat(response.getContent().get(0).getBedCount()).isEqualTo(60);
        verify(lazyListRepository)
                .findFloorListItemsByBuildingId(
                        buildingId, null, AccommodationStatus.AVAILABLE, AccommodationStatus.OCCUPIED, pageable);
    }
}
