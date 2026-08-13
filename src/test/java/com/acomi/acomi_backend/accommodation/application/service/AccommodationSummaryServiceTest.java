package com.acomi.acomi_backend.accommodation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.accommodation.api.dto.response.BuildingSummaryResponse;
import com.acomi.acomi_backend.accommodation.domain.model.AccommodationStatus;
import com.acomi.acomi_backend.accommodation.domain.policy.AccommodationProfileResolver;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.AccommodationSummaryRepository;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.BuildingRepository;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
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
class AccommodationSummaryServiceTest {

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private SpaceMembershipRepository spaceMembershipRepository;

    @Spy
    private AccommodationProfileResolver profileResolver = new AccommodationProfileResolver();

    @Mock
    private BuildingRepository buildingRepository;

    @Mock
    private AccommodationSummaryRepository summaryRepository;

    private AccommodationAccessService accessService;

    private AccommodationSummaryService summaryService;

    private UUID spaceId;
    private UUID buildingId;
    private UUID callerId;

    @BeforeEach
    void setUp() {
        accessService = AccommodationAccessTestSupport.accessService(
                spaceRepository, spaceMembershipRepository, profileResolver);
        summaryService = new AccommodationSummaryService(accessService, buildingRepository, summaryRepository);
        spaceId = UUID.randomUUID();
        buildingId = UUID.randomUUID();
        callerId = UUID.randomUUID();
    }

    @Test
    void getBuildingSummary_aggregatesCountsAndStatuses() {
        SpaceEntity space = SpaceEntity.builder().name("PG").type(SpaceType.PG).isActive(true).build();
        space.setId(spaceId);

        BuildingEntity building = BuildingEntity.builder()
                .space(space)
                .name("Sunrise PG")
                .code("SUN")
                .build();
        building.setId(buildingId);

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        AccommodationAccessTestSupport.stubMembership(
                spaceMembershipRepository, callerId, spaceId, space, com.acomi.acomi_backend.member.domain.model.MembershipRole.STAFF);
        when(buildingRepository.findActiveByIdAndSpaceId(buildingId, spaceId)).thenReturn(Optional.of(building));
        when(summaryRepository.countActiveFloors(buildingId)).thenReturn(3L);
        when(summaryRepository.countActiveUnits(buildingId)).thenReturn(0L);
        when(summaryRepository.countVisibleActiveUnits(buildingId)).thenReturn(0L);
        when(summaryRepository.countSyntheticActiveUnits(buildingId)).thenReturn(0L);
        when(summaryRepository.countActiveRooms(buildingId)).thenReturn(30L);
        when(summaryRepository.countActiveBeds(buildingId)).thenReturn(90L);
        when(summaryRepository.countRoomStatuses(buildingId))
                .thenReturn(List.<Object[]>of(new Object[] {AccommodationStatus.AVAILABLE, 30L}));
        when(summaryRepository.countBedStatuses(buildingId))
                .thenReturn(List.<Object[]>of(new Object[] {AccommodationStatus.AVAILABLE, 90L}));
        when(summaryRepository.countUnitStatuses(buildingId)).thenReturn(List.of());

        BuildingSummaryResponse response = summaryService.getBuildingSummary(spaceId, buildingId, callerId);

        assertThat(response.getName()).isEqualTo("Sunrise PG");
        assertThat(response.getCounts().getFloors()).isEqualTo(3);
        assertThat(response.getCounts().getBeds()).isEqualTo(90);
        assertThat(response.getStatusCounts().getAvailable()).isEqualTo(90);
    }
}
