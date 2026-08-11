package com.amico.amico_backend.accommodation.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.amico.amico_backend.accommodation.api.dto.request.BulkCreateRoomsRequest;
import com.amico.amico_backend.accommodation.api.dto.request.BulkCreateUnitsRequest;
import com.amico.amico_backend.accommodation.domain.model.RoomType;
import com.amico.amico_backend.accommodation.domain.policy.AccommodationLimits;
import com.amico.amico_backend.accommodation.domain.policy.AccommodationNumberingService;
import com.amico.amico_backend.accommodation.domain.policy.AccommodationProfileResolver;
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
class AccommodationBulkServiceTest {

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private SpaceMembershipRepository spaceMembershipRepository;

    @Spy
    private AccommodationProfileResolver profileResolver = new AccommodationProfileResolver();

    @Mock
    private AccommodationNumberingService numberingService;

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

    private AccommodationAccessService accessService;

    private AccommodationProfileService profileService;
    private AccommodationLayoutService layoutService;
    private SyntheticUnitService syntheticUnitService;
    private AccommodationBulkService bulkService;

    private UUID spaceId;
    private UUID callerId;

    @BeforeEach
    void setUp() {
        accessService = AccommodationAccessTestSupport.accessService(
                spaceRepository, spaceMembershipRepository, profileResolver);
        profileService = new AccommodationProfileService(accessService);
        layoutService = new AccommodationLayoutService();
        syntheticUnitService = new SyntheticUnitService(unitRepository, numberingService);
        bulkService = new AccommodationBulkService(
                accessService,
                profileService,
                layoutService,
                syntheticUnitService,
                numberingService,
                buildingRepository,
                floorRepository,
                unitRepository,
                roomRepository,
                bedRepository);

        spaceId = UUID.randomUUID();
        callerId = UUID.randomUUID();
    }

    @Test
    void bulkCreateRooms_rejectsRentalBeds() {
        SpaceEntity rentalSpace = SpaceEntity.builder()
                .name("Rental")
                .type(SpaceType.RENTAL)
                .isActive(true)
                .build();
        rentalSpace.setId(spaceId);

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(rentalSpace));

        BulkCreateRoomsRequest request = new BulkCreateRoomsRequest();
        setField(request, "count", 2);
        setField(request, "roomType", RoomType.PRIVATE);
        setField(request, "capacity", 1);
        setField(request, "bedsPerRoom", 2);

        assertThatThrownBy(() -> bulkService.bulkCreateRoomsUnderUnit(spaceId, UUID.randomUUID(), callerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("RENTAL");
    }

    @Test
    void bulkCreateUnits_rejectsCountAboveLimit() {
        SpaceEntity rentalSpace = SpaceEntity.builder()
                .name("Rental")
                .type(SpaceType.RENTAL)
                .isActive(true)
                .build();
        rentalSpace.setId(spaceId);

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(rentalSpace));
        AccommodationAccessTestSupport.stubMembership(
                spaceMembershipRepository, callerId, spaceId, rentalSpace, com.amico.amico_backend.member.domain.model.MembershipRole.MANAGER);

        BulkCreateUnitsRequest request = new BulkCreateUnitsRequest();
        setField(request, "count", AccommodationLimits.MAX_UNITS_PER_BULK + 1);

        assertThatThrownBy(() -> bulkService.bulkCreateUnits(spaceId, UUID.randomUUID(), callerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("50");
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
