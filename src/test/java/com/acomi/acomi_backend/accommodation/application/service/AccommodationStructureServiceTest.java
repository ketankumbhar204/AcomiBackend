package com.acomi.acomi_backend.accommodation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.accommodation.api.dto.request.CreateBedRequest;
import com.acomi.acomi_backend.accommodation.api.dto.request.CreateBuildingRequest;
import com.acomi.acomi_backend.accommodation.api.dto.request.CreateFloorRequest;
import com.acomi.acomi_backend.accommodation.api.dto.request.CreateRoomRequest;
import com.acomi.acomi_backend.accommodation.api.dto.request.CreateUnitRequest;
import com.acomi.acomi_backend.accommodation.api.dto.response.BuildingResponse;
import com.acomi.acomi_backend.accommodation.domain.model.PropertyLayoutMode;
import com.acomi.acomi_backend.accommodation.domain.model.RoomType;
import com.acomi.acomi_backend.accommodation.domain.policy.AccommodationNumberingService;
import com.acomi.acomi_backend.accommodation.domain.policy.AccommodationProfileResolver;
import com.acomi.acomi_backend.accommodation.domain.policy.PropertyLayoutModeResolver;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.FloorEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.RoomEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.UnitEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.BedRepository;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.BuildingRepository;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.FloorRepository;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.RoomRepository;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.UnitRepository;
import com.acomi.acomi_backend.occupancy.application.service.OccupancyService;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.repository.OccupancyRepository;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.application.service.SpaceMembershipResolver;
import com.acomi.acomi_backend.member.domain.model.MembershipStatus;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AccommodationStructureServiceTest {

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private SpaceMembershipRepository spaceMembershipRepository;

    @Spy
    private AccommodationProfileResolver profileResolver = new AccommodationProfileResolver();

    @Spy
    private PropertyLayoutModeResolver layoutModeResolver = new PropertyLayoutModeResolver();

    @Spy
    private AccommodationNumberingService numberingService = new AccommodationNumberingService();

    @Mock
    private BuildingRepository buildingRepository;

    @Mock
    private FloorRepository floorRepository;

    @Mock
    private UnitRepository unitRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private BedRepository bedRepository;

    @Mock
    private AccommodationActionService actionService;

    @Mock
    private OccupancyService occupancyService;

    @Mock
    private OccupancyRepository occupancyRepository;

    private SpaceMembershipResolver membershipResolver;

    private AccommodationAccessService accessService;

    private AccommodationProfileService profileService;
    private AccommodationLayoutService layoutService;
    private SyntheticUnitService syntheticUnitService;
    private BuildingService buildingService;
    private FloorService floorService;
    private RoomService roomService;
    private BedService bedService;

    private UUID spaceId;
    private UUID ownerId;
    private UUID buildingId;
    private UUID floorId;
    private UUID unitId;
    private UUID roomId;
    private SpaceEntity pgSpace;
    private SpaceEntity coLivingSpace;
    private SpaceEntity rentalSpace;
    private SpaceEntity messSpace;
    private BuildingEntity building;

    @BeforeEach
    void setUp() {
        membershipResolver = new SpaceMembershipResolver(spaceMembershipRepository);
        accessService = new AccommodationAccessService(spaceRepository, membershipResolver, profileResolver);
        profileService = new AccommodationProfileService(accessService);
        layoutService = new AccommodationLayoutService();
        syntheticUnitService = new SyntheticUnitService(unitRepository, numberingService);
        buildingService = new BuildingService(
                buildingRepository,
                floorRepository,
                unitRepository,
                accessService,
                actionService,
                layoutModeResolver);
        floorService = new FloorService(
                floorRepository,
                buildingRepository,
                roomRepository,
                unitRepository,
                accessService,
                profileService,
                actionService);
        roomService = new RoomService(
                roomRepository,
                floorRepository,
                unitRepository,
                bedRepository,
                accessService,
                profileService,
                actionService,
                layoutService,
                syntheticUnitService);
        bedService = new BedService(
                bedRepository,
                roomRepository,
                accessService,
                profileService,
                actionService,
                occupancyService,
                occupancyRepository,
                new BedPricingPropagationService(bedRepository));

        spaceId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        buildingId = UUID.randomUUID();
        floorId = UUID.randomUUID();
        unitId = UUID.randomUUID();
        roomId = UUID.randomUUID();

        UserEntity owner = UserEntity.builder().fullName("Owner").mobileNumber("9876543210").build();
        owner.setId(ownerId);

        pgSpace = SpaceEntity.builder()
                .owner(owner)
                .name("Sunrise PG")
                .type(SpaceType.PG)
                .isActive(true)
                .build();
        pgSpace.setId(spaceId);

        coLivingSpace = SpaceEntity.builder()
                .owner(owner)
                .name("Urban Co-Living")
                .type(SpaceType.CO_LIVING)
                .isActive(true)
                .build();
        coLivingSpace.setId(spaceId);

        rentalSpace = SpaceEntity.builder()
                .owner(owner)
                .name("Rental Block")
                .type(SpaceType.RENTAL)
                .isActive(true)
                .build();
        rentalSpace.setId(spaceId);

        messSpace = SpaceEntity.builder()
                .owner(owner)
                .name("Office Mess")
                .type(SpaceType.MESS)
                .isActive(true)
                .build();
        messSpace.setId(spaceId);

        building = BuildingEntity.builder()
                .space(pgSpace)
                .name("Building A")
                .layoutMode(PropertyLayoutMode.CORRIDOR_PG)
                .isActive(true)
                .build();
        building.setId(buildingId);
    }

    @Test
    void createBuilding_whenPgSpace_createsBuilding() {
        CreateBuildingRequest request = new CreateBuildingRequest();
        setField(request, "name", "Building A");

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(pgSpace));
        stubOwnerMembership(pgSpace);
        when(buildingRepository.existsBySpaceIdAndNameAndIsActiveTrue(spaceId, "Building A"))
                .thenReturn(false);
        when(buildingRepository.save(any(BuildingEntity.class))).thenAnswer(invocation -> {
            BuildingEntity saved = invocation.getArgument(0);
            saved.setId(buildingId);
            return saved;
        });

        BuildingResponse response = buildingService.createBuilding(spaceId, ownerId, request);

        assertThat(response.getBuildingId()).isEqualTo(buildingId);
        assertThat(response.getName()).isEqualTo("Building A");
    }

    @Test
    void createBuilding_whenMessSpace_throwsBusinessException() {
        CreateBuildingRequest request = new CreateBuildingRequest();
        setField(request, "name", "Building A");

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(messSpace));

        assertThatThrownBy(() -> buildingService.createBuilding(spaceId, ownerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Accommodation is not applicable for Mess spaces")
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(buildingRepository, never()).save(any());
    }

    @Test
    void deactivateBuilding_whenActiveFloorsExist_throwsBusinessException() {
        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(pgSpace));
        stubOwnerMembership(pgSpace);
        when(buildingRepository.findActiveByIdAndSpaceId(buildingId, spaceId))
                .thenReturn(Optional.of(building));
        when(floorRepository.existsByBuildingIdAndIsActiveTrue(buildingId)).thenReturn(true);

        assertThatThrownBy(() -> buildingService.deactivateBuilding(spaceId, buildingId, ownerId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("active floors exist");

        verify(buildingRepository, never()).save(any());
    }

    @Test
    void createFloor_whenCoLivingSpace_throwsBusinessException() {
        CreateFloorRequest request = new CreateFloorRequest();
        setField(request, "name", "Ground Floor");
        setField(request, "floorNumber", 0);

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(coLivingSpace));

        assertThatThrownBy(() -> floorService.createFloor(spaceId, buildingId, ownerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Floors are not supported");

        verify(floorRepository, never()).save(any());
    }

    @Test
    void createRoomUnderUnit_whenRentalSpace_throwsBusinessException() {
        CreateRoomRequest request = new CreateRoomRequest();
        setField(request, "name", "Bedroom");
        setField(request, "roomNumber", "1");
        setField(request, "roomType", RoomType.PRIVATE);
        setField(request, "capacity", 1);

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(rentalSpace));
        stubOwnerMembership(rentalSpace);

        UnitEntity unit = UnitEntity.builder()
                .building(BuildingEntity.builder()
                        .space(rentalSpace)
                        .layoutMode(PropertyLayoutMode.RENTAL)
                        .name("Rental Block")
                        .build())
                .name("Unit 1")
                .unitNumber("1")
                .build();
        unit.setId(unitId);
        when(unitRepository.findActiveByIdAndSpaceId(unitId, spaceId)).thenReturn(Optional.of(unit));

        assertThatThrownBy(() -> roomService.createRoomUnderUnit(spaceId, unitId, ownerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("rental layout");

        verify(roomRepository, never()).save(any());
    }

    @Test
    void createBed_whenRentalSpace_throwsBusinessException() {
        CreateBedRequest request = new CreateBedRequest();
        setField(request, "name", "Bed A");
        setField(request, "bedNumber", "A");

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(rentalSpace));

        assertThatThrownBy(() -> bedService.createBed(spaceId, roomId, ownerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Beds are not supported");

        verify(bedRepository, never()).save(any());
    }

    @Test
    void createRoomUnderFloor_whenPgSpace_createsRoomWithRequiredRoomType() {
        CreateRoomRequest request = new CreateRoomRequest();
        setField(request, "name", "Room 101");
        setField(request, "roomNumber", "101");
        setField(request, "roomType", RoomType.SHARED);
        setField(request, "capacity", 2);

        FloorEntity floor = FloorEntity.builder()
                .building(building)
                .name("Ground Floor")
                .floorNumber(0)
                .isActive(true)
                .build();
        floor.setId(floorId);

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(pgSpace));
        stubOwnerMembership(pgSpace);
        when(floorRepository.findActiveByIdAndSpaceId(floorId, spaceId)).thenReturn(Optional.of(floor));
        when(unitRepository.save(any(UnitEntity.class))).thenAnswer(invocation -> {
            UnitEntity unit = invocation.getArgument(0);
            unit.setId(unitId);
            assertThat(unit.isSynthetic()).isTrue();
            return unit;
        });
        when(roomRepository.save(any(RoomEntity.class))).thenAnswer(invocation -> {
            RoomEntity room = invocation.getArgument(0);
            room.setId(roomId);
            assertThat(room.getRoomType()).isEqualTo(RoomType.SHARED);
            return room;
        });

        var response = roomService.createRoomUnderFloor(spaceId, floorId, ownerId, request);

        assertThat(response.getRoomType()).isEqualTo(RoomType.SHARED);
        assertThat(response.getCapacity()).isEqualTo(2);
    }

    @Test
    void deactivateFloor_whenActiveRoomsExist_throwsBusinessException() {
        FloorEntity floor = FloorEntity.builder()
                .building(building)
                .name("Ground Floor")
                .floorNumber(0)
                .isActive(true)
                .build();
        floor.setId(floorId);

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(pgSpace));
        stubOwnerMembership(pgSpace);
        when(floorRepository.findActiveByIdAndBuildingId(floorId, buildingId)).thenReturn(Optional.of(floor));
        when(buildingRepository.findActiveByIdAndSpaceId(buildingId, spaceId)).thenReturn(Optional.of(building));
        when(roomRepository.existsByFloorIdAndIsActiveTrue(floorId)).thenReturn(true);

        assertThatThrownBy(() -> floorService.deactivateFloor(spaceId, buildingId, floorId, ownerId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("active rooms exist");

        verify(floorRepository, never()).save(any());
    }

    @Test
    void getBuildings_whenCallerBelongsToSpace_returnsList() {
        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(pgSpace));
        stubOwnerMembership(pgSpace);
        when(buildingRepository.findActiveBySpaceId(spaceId)).thenReturn(java.util.List.of(building));

        var buildings = buildingService.getBuildings(spaceId, ownerId);

        assertThat(buildings).hasSize(1);
        assertThat(buildings.get(0).getName()).isEqualTo("Building A");
    }

    @Test
    void createRoomUnderUnit_whenCoLiving_createsRoom() {
        CreateRoomRequest request = new CreateRoomRequest();
        setField(request, "name", "Room A");
        setField(request, "roomNumber", "A");
        setField(request, "roomType", RoomType.DORMITORY);
        setField(request, "capacity", 4);

        BuildingEntity coBuilding = BuildingEntity.builder()
                .space(coLivingSpace)
                .name("Block 1")
                .layoutMode(PropertyLayoutMode.CO_LIVING)
                .isActive(true)
                .build();
        coBuilding.setId(buildingId);

        UnitEntity unit = UnitEntity.builder()
                .building(coBuilding)
                .name("Unit 1")
                .unitNumber("1")
                .isActive(true)
                .build();
        unit.setId(unitId);

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(coLivingSpace));
        stubOwnerMembership(coLivingSpace);
        when(unitRepository.findActiveByIdAndSpaceId(unitId, spaceId)).thenReturn(Optional.of(unit));
        when(roomRepository.save(any(RoomEntity.class))).thenAnswer(invocation -> {
            RoomEntity room = invocation.getArgument(0);
            room.setId(roomId);
            return room;
        });

        var response = roomService.createRoomUnderUnit(spaceId, unitId, ownerId, request);

        assertThat(response.getRoomType()).isEqualTo(RoomType.DORMITORY);
    }

    private void stubOwnerMembership(SpaceEntity space) {
        stubMembership(ownerId, MembershipRole.OWNER, space);
    }

    private void stubMembership(UUID userId, MembershipRole role, SpaceEntity space) {
        UserEntity user = UserEntity.builder().fullName("Owner").mobileNumber("9876543210").build();
        user.setId(userId);
        SpaceMembershipEntity membership = SpaceMembershipEntity.builder()
                .user(user)
                .space(space)
                .role(role)
                .status(MembershipStatus.ACTIVE)
                .build();
        when(spaceMembershipRepository.findMembershipByUserAndSpace(userId, spaceId))
                .thenReturn(Optional.of(membership));
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
