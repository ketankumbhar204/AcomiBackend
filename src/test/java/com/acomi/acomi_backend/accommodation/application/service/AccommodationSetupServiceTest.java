package com.acomi.acomi_backend.accommodation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.accommodation.api.dto.request.setup.AccommodationSetupRequest;
import com.acomi.acomi_backend.accommodation.api.dto.request.setup.BuildingSetupInput;
import com.acomi.acomi_backend.accommodation.api.dto.request.setup.PgHostelSetupConfig;
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
