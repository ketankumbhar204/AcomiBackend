package com.amico.amico_backend.accommodation.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amico.amico_backend.accommodation.api.dto.request.DuplicateBuildingRequest;
import com.amico.amico_backend.accommodation.api.dto.request.DuplicateFloorRequest;
import com.amico.amico_backend.accommodation.domain.policy.AccommodationNumberingService;
import com.amico.amico_backend.accommodation.domain.policy.AccommodationProfileResolver;
import com.amico.amico_backend.accommodation.domain.model.PropertyLayoutMode;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.FloorEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.UnitEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.repository.BedRepository;
import com.amico.amico_backend.accommodation.infrastructure.persistence.repository.BuildingRepository;
import com.amico.amico_backend.accommodation.infrastructure.persistence.repository.FloorRepository;
import com.amico.amico_backend.accommodation.infrastructure.persistence.repository.RoomRepository;
import com.amico.amico_backend.accommodation.infrastructure.persistence.repository.UnitRepository;
import com.amico.amico_backend.common.exception.BusinessException;
import com.amico.amico_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.amico.amico_backend.space.domain.model.SpaceType;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.amico.amico_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccommodationDuplicateServiceTest {

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private SpaceMembershipRepository spaceMembershipRepository;

    @Spy
    private AccommodationProfileResolver profileResolver = new AccommodationProfileResolver();

    @Spy
    private AccommodationNumberingService numberingService = new AccommodationNumberingService();

    @Mock
    private BuildingRepository buildingRepository;

    @Mock
    private FloorRepository floorRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private UnitRepository unitRepository;

    @Mock
    private BedRepository bedRepository;

    private AccommodationAccessService accessService;

    private AccommodationProfileService profileService;
    private AccommodationDuplicateService duplicateService;

    private UUID spaceId;
    private UUID buildingId;
    private UUID floorId;
    private UUID callerId;

    @BeforeEach
    void setUp() {
        accessService = AccommodationAccessTestSupport.accessService(
                spaceRepository, spaceMembershipRepository, profileResolver);
        profileService = new AccommodationProfileService(accessService);
        duplicateService = new AccommodationDuplicateService(
                accessService,
                profileService,
                numberingService,
                buildingRepository,
                floorRepository,
                unitRepository,
                roomRepository,
                bedRepository);

        spaceId = UUID.randomUUID();
        buildingId = UUID.randomUUID();
        floorId = UUID.randomUUID();
        callerId = UUID.randomUUID();
    }

    @Test
    void duplicateFloor_rejectsConflictingFloorNumber() {
        SpaceEntity space = SpaceEntity.builder().name("PG").type(SpaceType.PG).isActive(true).build();
        space.setId(spaceId);
        BuildingEntity building = BuildingEntity.builder().space(space).name("B1").build();
        building.setId(buildingId);
        FloorEntity sourceFloor = FloorEntity.builder()
                .building(building)
                .name("Floor 1")
                .floorNumber(1)
                .build();
        sourceFloor.setId(floorId);

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        AccommodationAccessTestSupport.stubMembership(
                spaceMembershipRepository, callerId, spaceId, space, com.amico.amico_backend.member.domain.model.MembershipRole.MANAGER);
        when(buildingRepository.findActiveByIdAndSpaceId(buildingId, spaceId)).thenReturn(Optional.of(building));
        when(floorRepository.findActiveByIdAndBuildingId(floorId, buildingId)).thenReturn(Optional.of(sourceFloor));
        when(floorRepository.existsByBuildingIdAndFloorNumberAndIsActiveTrue(buildingId, 2)).thenReturn(true);

        DuplicateFloorRequest request = new DuplicateFloorRequest();
        setField(request, "targetFloorNumber", 2);

        assertThatThrownBy(() ->
                        duplicateService.duplicateFloor(spaceId, buildingId, floorId, callerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("floor number");
    }

    @Test
    void duplicateFloor_apartmentPg_renumbersUnitsForTargetFloor() {
        SpaceEntity space = SpaceEntity.builder().name("PG").type(SpaceType.PG).isActive(true).build();
        space.setId(spaceId);
        BuildingEntity building = BuildingEntity.builder()
                .space(space)
                .name("B1")
                .layoutMode(PropertyLayoutMode.APARTMENT_PG)
                .build();
        building.setId(buildingId);
        FloorEntity sourceFloor = FloorEntity.builder()
                .building(building)
                .name("Ground Floor")
                .floorNumber(0)
                .build();
        sourceFloor.setId(floorId);
        UnitEntity sourceUnit = UnitEntity.builder()
                .building(building)
                .floor(sourceFloor)
                .name("Unit 101")
                .unitNumber("101")
                .synthetic(false)
                .build();
        sourceUnit.setId(UUID.randomUUID());

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        AccommodationAccessTestSupport.stubMembership(
                spaceMembershipRepository, callerId, spaceId, space, com.amico.amico_backend.member.domain.model.MembershipRole.MANAGER);
        when(buildingRepository.findActiveByIdAndSpaceId(buildingId, spaceId)).thenReturn(Optional.of(building));
        when(floorRepository.findActiveByIdAndBuildingId(floorId, buildingId)).thenReturn(Optional.of(sourceFloor));
        when(floorRepository.existsByBuildingIdAndFloorNumberAndIsActiveTrue(buildingId, 5)).thenReturn(false);
        when(floorRepository.existsByBuildingIdAndFloorNumberAndIsActiveTrue(buildingId, 0)).thenReturn(true);
        when(unitRepository.findActiveByFloorId(floorId, false)).thenReturn(List.of(sourceUnit));
        when(unitRepository.existsByBuildingIdAndUnitNumberAndIsActiveTrue(buildingId, "601")).thenReturn(false);
        when(roomRepository.findActiveByUnitId(sourceUnit.getId())).thenReturn(List.of());
        when(floorRepository.save(any(FloorEntity.class))).thenAnswer(invocation -> {
            FloorEntity floor = invocation.getArgument(0);
            floor.setId(UUID.randomUUID());
            return floor;
        });
        when(unitRepository.save(any(UnitEntity.class))).thenAnswer(invocation -> {
            UnitEntity unit = invocation.getArgument(0);
            unit.setId(UUID.randomUUID());
            return unit;
        });

        DuplicateFloorRequest request = new DuplicateFloorRequest();
        setField(request, "targetFloorNumber", 5);
        setField(request, "renumberRooms", true);

        duplicateService.duplicateFloor(spaceId, buildingId, floorId, callerId, request);

        verify(unitRepository).save(org.mockito.ArgumentMatchers.argThat(unit ->
                "601".equals(unit.getUnitNumber())));
        verify(unitRepository, never()).save(org.mockito.ArgumentMatchers.argThat(unit ->
                "101".equals(unit.getUnitNumber())));
    }

    @Test
    void duplicateBuilding_rejectsDuplicateName() {
        SpaceEntity space = SpaceEntity.builder().name("PG").type(SpaceType.PG).isActive(true).build();
        space.setId(spaceId);
        BuildingEntity building = BuildingEntity.builder().space(space).name("Building A").build();
        building.setId(buildingId);

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        AccommodationAccessTestSupport.stubMembership(
                spaceMembershipRepository, callerId, spaceId, space, com.amico.amico_backend.member.domain.model.MembershipRole.MANAGER);
        when(buildingRepository.findActiveByIdAndSpaceId(buildingId, spaceId)).thenReturn(Optional.of(building));
        when(buildingRepository.existsBySpaceIdAndNameAndIsActiveTrue(spaceId, "Building B")).thenReturn(true);

        DuplicateBuildingRequest request = new DuplicateBuildingRequest();
        setField(request, "targetBuildingName", "Building B");

        assertThatThrownBy(() ->
                        duplicateService.duplicateBuilding(spaceId, buildingId, callerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("building with this name");
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }
}
