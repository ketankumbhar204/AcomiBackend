package com.acomi.acomi_backend.accommodation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.accommodation.api.dto.request.setup.AccommodationSetupRequest;
import com.acomi.acomi_backend.accommodation.api.dto.request.setup.BuildingSetupInput;
import com.acomi.acomi_backend.accommodation.api.dto.request.setup.PgHostelSetupConfig;
import com.acomi.acomi_backend.accommodation.api.dto.request.setup.UnitSetupConfig;
import com.acomi.acomi_backend.accommodation.domain.model.PropertyLayoutMode;
import com.acomi.acomi_backend.accommodation.api.dto.response.setup.AccommodationSetupPreviewResponse;
import com.acomi.acomi_backend.accommodation.domain.model.RoomType;
import com.acomi.acomi_backend.accommodation.domain.policy.AccommodationNumberingService;
import com.acomi.acomi_backend.accommodation.domain.policy.AccommodationProfileResolver;
import com.acomi.acomi_backend.accommodation.domain.policy.PropertyLayoutModeResolver;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.AccommodationSetupIdempotencyRepository;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.BedRepository;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.BuildingRepository;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.FloorRepository;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.RoomRepository;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.UnitRepository;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class AccommodationSetupServiceTest {

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private SpaceMembershipRepository spaceMembershipRepository;

    @Spy
    private AccommodationProfileResolver profileResolver = new AccommodationProfileResolver();

    @Spy
    private AccommodationNumberingService numberingService = new AccommodationNumberingService();

    @Spy
    private PropertyLayoutModeResolver layoutModeResolver = new PropertyLayoutModeResolver();

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
    private AccommodationSetupIdempotencyRepository idempotencyRepository;

    @Mock
    private UserRepository userRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private AccommodationAccessService accessService;

    private AccommodationProfileService profileService;
    private AccommodationSetupService setupService;

    private UUID spaceId;
    private UUID ownerId;
    private SpaceEntity pgSpace;

    @BeforeEach
    void setUp() {
        accessService = AccommodationAccessTestSupport.accessService(
                spaceRepository, spaceMembershipRepository, profileResolver);
        profileService = new AccommodationProfileService(accessService);
        setupService = new AccommodationSetupService(
                accessService,
                profileService,
                layoutModeResolver,
                numberingService,
                buildingRepository,
                floorRepository,
                unitRepository,
                roomRepository,
                bedRepository,
                idempotencyRepository,
                userRepository,
                objectMapper);

        spaceId = UUID.randomUUID();
        ownerId = UUID.randomUUID();

        pgSpace = SpaceEntity.builder()
                .name("Sunrise PG")
                .type(SpaceType.PG)
                .isActive(true)
                .build();
        pgSpace.setId(spaceId);
    }

    @Test
    void preview_computesPgTotalsWithoutPersistence() {
        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(pgSpace));
        AccommodationAccessTestSupport.stubMembership(
                spaceMembershipRepository, ownerId, spaceId, pgSpace, com.acomi.acomi_backend.member.domain.model.MembershipRole.OWNER);

        AccommodationSetupPreviewResponse response =
                setupService.preview(spaceId, ownerId, pgSetupRequest());

        assertThat(response.getTotals().getFloors()).isEqualTo(2);
        assertThat(response.getTotals().getRooms()).isEqualTo(4);
        assertThat(response.getTotals().getBeds()).isEqualTo(8);
        assertThat(response.getSample()).hasSize(1);
        verify(buildingRepository, never()).save(any());
    }

    @Test
    void preview_rejectsSpaceTypeMismatch() {
        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(pgSpace));
        AccommodationAccessTestSupport.stubMembership(
                spaceMembershipRepository, ownerId, spaceId, pgSpace, com.acomi.acomi_backend.member.domain.model.MembershipRole.OWNER);

        AccommodationSetupRequest request = pgSetupRequest();
        setField(request, "spaceType", SpaceType.HOSTEL);

        assertThatThrownBy(() -> setupService.preview(spaceId, ownerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("spaceType")
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void execute_requiresIdempotencyKey() {
        assertThatThrownBy(() -> setupService.execute(spaceId, ownerId, pgSetupRequest(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Idempotency-Key");
    }

    @Test
    void checkBuildingAvailability_rejectsActiveDuplicateName() {
        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(pgSpace));
        AccommodationAccessTestSupport.stubMembership(
                spaceMembershipRepository, ownerId, spaceId, pgSpace,
                com.acomi.acomi_backend.member.domain.model.MembershipRole.OWNER);
        when(buildingRepository.existsBySpaceIdAndNameIgnoreCaseAndIsActiveTrue(spaceId, "B1")).thenReturn(true);

        var request = new com.acomi.acomi_backend.accommodation.api.dto.request.setup.BuildingAvailabilityRequest();
        setField(request, "name", "B1");

        var response = setupService.checkBuildingAvailability(spaceId, ownerId, request);
        assertThat(response.isNameAvailable()).isFalse();
        assertThat(response.getMessage()).contains("already exists");
    }

    @Test
    void checkBuildingAvailability_allowsNameWhenNoActiveBuilding() {
        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(pgSpace));
        AccommodationAccessTestSupport.stubMembership(
                spaceMembershipRepository, ownerId, spaceId, pgSpace,
                com.acomi.acomi_backend.member.domain.model.MembershipRole.OWNER);
        when(buildingRepository.existsBySpaceIdAndNameIgnoreCaseAndIsActiveTrue(spaceId, "B2")).thenReturn(false);

        var request = new com.acomi.acomi_backend.accommodation.api.dto.request.setup.BuildingAvailabilityRequest();
        setField(request, "name", "B2");

        var response = setupService.checkBuildingAvailability(spaceId, ownerId, request);
        assertThat(response.isNameAvailable()).isTrue();
    }

    @Test
    void execute_rejectsDuplicateActiveBuildingName() {
        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(pgSpace));
        AccommodationAccessTestSupport.stubMembership(
                spaceMembershipRepository, ownerId, spaceId, pgSpace,
                com.acomi.acomi_backend.member.domain.model.MembershipRole.OWNER);
        when(idempotencyRepository.findBySpaceIdAndIdempotencyKey(spaceId, "key-1")).thenReturn(Optional.empty());
        when(buildingRepository.existsBySpaceIdAndNameAndIsActiveTrue(spaceId, "Sunrise PG")).thenReturn(true);

        assertThatThrownBy(() -> setupService.execute(spaceId, ownerId, pgSetupRequest(), "key-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");
        verify(buildingRepository, never()).save(any());
    }

    @Test
    void execute_persistsExplicitBedPricingFromStructure() {
        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(pgSpace));
        AccommodationAccessTestSupport.stubMembership(
                spaceMembershipRepository, ownerId, spaceId, pgSpace,
                com.acomi.acomi_backend.member.domain.model.MembershipRole.OWNER);
        when(idempotencyRepository.findBySpaceIdAndIdempotencyKey(spaceId, "key-2")).thenReturn(Optional.empty());
        when(buildingRepository.existsBySpaceIdAndNameAndIsActiveTrue(spaceId, "Sunrise PG")).thenReturn(false);
        when(userRepository.findByIdAndIsActiveTrue(ownerId))
                .thenReturn(Optional.of(com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity.builder()
                        .mobileNumber("9876543210")
                        .fullName("Owner")
                        .build()));
        when(buildingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(floorRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(unitRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(roomRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(bedRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(idempotencyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AccommodationSetupRequest request = pgSetupRequestWithPricedStructure();
        var response = setupService.execute(spaceId, ownerId, request, "key-2");

        assertThat(response.getTotals().getFloors()).isEqualTo(1);
        assertThat(response.getTotals().getRooms()).isEqualTo(1);
        assertThat(response.getTotals().getBeds()).isEqualTo(2);
        verify(bedRepository).saveAll(org.mockito.ArgumentMatchers.argThat(beds -> {
            var list = (java.util.List<?>) beds;
            return list.size() == 2;
        }));
    }

    @Test
    void preview_acceptsTheSamePgSetupForAnyLiveSpace() {
        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(pgSpace));
        AccommodationAccessTestSupport.stubMembership(
                spaceMembershipRepository, ownerId, spaceId, pgSpace,
                com.acomi.acomi_backend.member.domain.model.MembershipRole.OWNER);

        AccommodationSetupPreviewResponse response =
                setupService.preview(spaceId, ownerId, pgSetupRequest());

        assertThat(response.getTotals().getBeds()).isEqualTo(8);
        verify(buildingRepository, never()).save(any());
    }

    @Test
    void preview_coLivingRejectsPgLayoutAndCountsUnits() {
        SpaceEntity space = liveSpace(SpaceType.CO_LIVING, "Shared House");
        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        AccommodationAccessTestSupport.stubMembership(
                spaceMembershipRepository, ownerId, spaceId, space,
                com.acomi.acomi_backend.member.domain.model.MembershipRole.OWNER);

        AccommodationSetupRequest mismatched = unitSetupRequest(SpaceType.CO_LIVING, PropertyLayoutMode.CORRIDOR_PG);
        assertThatThrownBy(() -> setupService.preview(spaceId, ownerId, mismatched))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CO_LIVING");

        AccommodationSetupPreviewResponse response = setupService.preview(
                spaceId, ownerId, unitSetupRequest(SpaceType.CO_LIVING, PropertyLayoutMode.CO_LIVING));
        assertThat(response.getTotals().getUnits()).isEqualTo(2);
        assertThat(response.getTotals().getRooms()).isEqualTo(2);
        assertThat(response.getTotals().getBeds()).isEqualTo(2);
        verify(buildingRepository, never()).save(any());
    }

    @Test
    void preview_rentalAndCorporateStayCreateUnitsWithoutBeds() {
        for (SpaceType type : java.util.List.of(SpaceType.RENTAL, SpaceType.CORPORATE_STAY)) {
            SpaceEntity space = liveSpace(type, type.name());
            when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
            AccommodationAccessTestSupport.stubMembership(
                    spaceMembershipRepository, ownerId, spaceId, space,
                    com.acomi.acomi_backend.member.domain.model.MembershipRole.OWNER);

            AccommodationSetupRequest corridor = unitSetupRequest(type, PropertyLayoutMode.CORRIDOR_PG);
            assertThatThrownBy(() -> setupService.preview(spaceId, ownerId, corridor))
                    .isInstanceOf(BusinessException.class);

            AccommodationSetupPreviewResponse response = setupService.preview(
                    spaceId, ownerId, unitSetupRequest(type, PropertyLayoutMode.RENTAL));
            assertThat(response.getTotals().getUnits()).isEqualTo(2);
            assertThat(response.getTotals().getRooms()).isZero();
            assertThat(response.getTotals().getBeds()).isZero();
        }
        verify(buildingRepository, never()).save(any());
        verify(bedRepository, never()).saveAll(any());
    }

    @Test
    void preview_corporateStayRejectsMissingUnits() {
        SpaceEntity space = liveSpace(SpaceType.CORPORATE_STAY, "Corporate Stay");
        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        AccommodationAccessTestSupport.stubMembership(
                spaceMembershipRepository, ownerId, spaceId, space,
                com.acomi.acomi_backend.member.domain.model.MembershipRole.OWNER);

        AccommodationSetupRequest request = unitSetupRequest(SpaceType.CORPORATE_STAY, PropertyLayoutMode.RENTAL);
        setField(request, "units", null);

        assertThatThrownBy(() -> setupService.preview(spaceId, ownerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("units configuration is required for this space type");
        verify(buildingRepository, never()).save(any());
    }

    @Test
    void execute_corporateStayPersistsUnitsWithoutRoomsOrBeds() {
        SpaceEntity space = liveSpace(SpaceType.CORPORATE_STAY, "Corporate Stay");
        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        AccommodationAccessTestSupport.stubMembership(
                spaceMembershipRepository, ownerId, spaceId, space,
                com.acomi.acomi_backend.member.domain.model.MembershipRole.OWNER);
        when(idempotencyRepository.findBySpaceIdAndIdempotencyKey(spaceId, "corp-1")).thenReturn(Optional.empty());
        when(buildingRepository.existsBySpaceIdAndNameAndIsActiveTrue(spaceId, "Block A")).thenReturn(false);
        when(userRepository.findByIdAndIsActiveTrue(ownerId))
                .thenReturn(Optional.of(com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity.builder()
                        .mobileNumber("9876543210")
                        .fullName("Owner")
                        .build()));
        when(buildingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(unitRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(idempotencyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = setupService.execute(
                spaceId, ownerId, unitSetupRequest(SpaceType.CORPORATE_STAY, PropertyLayoutMode.RENTAL), "corp-1");

        assertThat(response.getTotals().getUnits()).isEqualTo(2);
        assertThat(response.getTotals().getFloors()).isZero();
        assertThat(response.getTotals().getRooms()).isZero();
        assertThat(response.getTotals().getBeds()).isZero();
        assertThat(response.isIdempotentReplay()).isFalse();
        verify(buildingRepository).save(any());
        verify(unitRepository, times(2)).save(any());
        verify(floorRepository, never()).save(any());
        verify(roomRepository, never()).save(any());
        verify(bedRepository, never()).saveAll(any());
    }

    @Test
    void preview_hostelAcceptsApartmentLayout() {
        SpaceEntity space = liveSpace(SpaceType.HOSTEL, "North Hostel");
        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        AccommodationAccessTestSupport.stubMembership(
                spaceMembershipRepository, ownerId, spaceId, space,
                com.acomi.acomi_backend.member.domain.model.MembershipRole.OWNER);

        AccommodationSetupRequest request = pgSetupRequest();
        setField(request, "spaceType", SpaceType.HOSTEL);
        setField(request, "layoutMode", PropertyLayoutMode.APARTMENT_PG);
        PgHostelSetupConfig floors = new PgHostelSetupConfig();
        setField(floors, "count", 1);
        setField(floors, "includeGroundFloor", false);
        setField(floors, "apartmentsPerFloor", 1);
        setField(floors, "roomsPerFloor", 1);
        setField(floors, "bedsPerRoom", 1);
        setField(floors, "defaultRoomType", RoomType.SHARED);
        setField(floors, "capacityPerRoom", 1);
        setField(request, "floors", floors);

        AccommodationSetupPreviewResponse response = setupService.preview(spaceId, ownerId, request);
        assertThat(response.getTotals().getFloors()).isEqualTo(1);
        assertThat(response.getTotals().getBeds()).isEqualTo(1);
        verify(buildingRepository, never()).save(any());
    }

    @Test
    void preview_messCannotUseAccommodationSetup() {
        SpaceEntity space = liveSpace(SpaceType.MESS, "Lunch Mess");
        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));

        assertThatThrownBy(() -> setupService.preview(spaceId, ownerId, pgSetupRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Mess");
        verify(buildingRepository, never()).save(any());
    }

    private SpaceEntity liveSpace(SpaceType type, String name) {
        SpaceEntity space = SpaceEntity.builder()
                .name(name)
                .type(type)
                .isActive(true)
                .build();
        space.setId(spaceId);
        return space;
    }

    private AccommodationSetupRequest unitSetupRequest(SpaceType type, PropertyLayoutMode layoutMode) {
        UnitSetupConfig units = new UnitSetupConfig();
        setField(units, "count", 2);
        setField(units, "roomsPerUnit", 1);
        setField(units, "bedsPerRoom", 1);
        setField(units, "defaultRoomType", RoomType.SHARED);
        setField(units, "capacityPerRoom", 1);

        BuildingSetupInput building = new BuildingSetupInput();
        setField(building, "name", "Block A");

        AccommodationSetupRequest request = new AccommodationSetupRequest();
        setField(request, "spaceType", type);
        setField(request, "layoutMode", layoutMode);
        setField(request, "building", building);
        setField(request, "units", units);
        return request;
    }

    private AccommodationSetupRequest pgSetupRequest() {
        PgHostelSetupConfig floors = new PgHostelSetupConfig();
        setField(floors, "count", 2);
        setField(floors, "includeGroundFloor", true);
        setField(floors, "roomsPerFloor", 2);
        setField(floors, "bedsPerRoom", 2);
        setField(floors, "defaultRoomType", RoomType.SHARED);
        setField(floors, "capacityPerRoom", 2);

        BuildingSetupInput building = new BuildingSetupInput();
        setField(building, "name", "Sunrise PG");

        AccommodationSetupRequest request = new AccommodationSetupRequest();
        setField(request, "spaceType", SpaceType.PG);
        setField(request, "building", building);
        setField(request, "floors", floors);
        return request;
    }

    private AccommodationSetupRequest pgSetupRequestWithPricedStructure() {
        AccommodationSetupRequest request = pgSetupRequest();
        var bedA = new com.acomi.acomi_backend.accommodation.api.dto.request.setup.SetupStructureInput.SetupBedNodeInput();
        setField(bedA, "name", "Bed A");
        setField(bedA, "number", "A");
        setField(bedA, "defaultRent", new java.math.BigDecimal("5000"));
        setField(bedA, "defaultDeposit", new java.math.BigDecimal("10000"));
        var bedB = new com.acomi.acomi_backend.accommodation.api.dto.request.setup.SetupStructureInput.SetupBedNodeInput();
        setField(bedB, "name", "Bed B");
        setField(bedB, "number", "B");
        var room = new com.acomi.acomi_backend.accommodation.api.dto.request.setup.SetupStructureInput.SetupRoomNodeInput();
        setField(room, "name", "Room 101");
        setField(room, "number", "101");
        setField(room, "capacity", 2);
        setField(room, "beds", java.util.List.of(bedA, bedB));
        var floor = new com.acomi.acomi_backend.accommodation.api.dto.request.setup.SetupStructureInput.SetupFloorNodeInput();
        setField(floor, "name", "Floor 1");
        setField(floor, "number", 1);
        setField(floor, "rooms", java.util.List.of(room));
        var structure = new com.acomi.acomi_backend.accommodation.api.dto.request.setup.SetupStructureInput();
        setField(structure, "floors", java.util.List.of(floor));
        setField(request, "structure", structure);
        return request;
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
