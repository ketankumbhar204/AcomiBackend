package com.amico.amico_backend.occupancy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amico.amico_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.amico.amico_backend.occupancy.infrastructure.persistence.entity.OccupancyAmenityEntity;
import com.amico.amico_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.amico.amico_backend.occupancy.infrastructure.persistence.repository.OccupancyAmenityRepository;
import com.amico.amico_backend.space.api.dto.AmenityAssignmentDto;
import com.amico.amico_backend.space.application.service.SpaceAmenityService;
import com.amico.amico_backend.space.domain.model.SpaceType;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OccupancyAmenityServiceTest {

    @Mock
    private OccupancyAmenityRepository occupancyAmenityRepository;

    @Mock
    private SpaceAmenityService spaceAmenityService;

    @InjectMocks
    private OccupancyAmenityService occupancyAmenityService;

    private OccupancyEntity occupancy;
    private UUID spaceId;

    @BeforeEach
    void setUp() {
        spaceId = UUID.randomUUID();
        SpaceEntity space = SpaceEntity.builder().type(SpaceType.PG).build();
        space.setId(spaceId);
        MemberEntity member = MemberEntity.builder().fullName("Ravi").mobileNumber("9999999999").space(space).build();
        occupancy = OccupancyEntity.builder().space(space).member(member).build();
        occupancy.setId(UUID.randomUUID());
    }

    @Test
    void applyToOccupancy_usesExplicitRequestWhenProvided() {
        List<AmenityAssignmentDto> spaceAmenities =
                List.of(dto("WIFI", "WiFi"), dto("PARKING", "Parking"));
        when(spaceAmenityService.getForSpace(spaceId)).thenReturn(spaceAmenities);
        when(occupancyAmenityRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        occupancyAmenityService.applyToOccupancy(
                occupancy, List.of(dto("WIFI", "WiFi")), null);

        ArgumentCaptor<OccupancyAmenityEntity> captor = ArgumentCaptor.forClass(OccupancyAmenityEntity.class);
        verify(occupancyAmenityRepository).deleteByOccupancyId(occupancy.getId());
        verify(occupancyAmenityRepository).save(captor.capture());
        assertThat(captor.getValue().getAmenityCode()).isEqualTo("WIFI");
    }

    @Test
    void applyToOccupancy_defaultsToAllSpaceAmenitiesWhenRequestNull() {
        List<AmenityAssignmentDto> spaceAmenities =
                List.of(dto("WIFI", "WiFi"), dto("PARKING", "Parking"));
        when(spaceAmenityService.getForSpace(spaceId)).thenReturn(spaceAmenities);
        when(occupancyAmenityRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        occupancyAmenityService.applyToOccupancy(occupancy, null, null);

        verify(occupancyAmenityRepository, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void applyToOccupancy_usesFallbackWhenRequestNull() {
        List<AmenityAssignmentDto> spaceAmenities = List.of(dto("WIFI", "WiFi"), dto("PARKING", "Parking"));
        List<AmenityAssignmentDto> fallback = List.of(dto("WIFI", "WiFi"));
        when(spaceAmenityService.getForSpace(spaceId)).thenReturn(spaceAmenities);
        when(occupancyAmenityRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        occupancyAmenityService.applyToOccupancy(occupancy, null, fallback);

        verify(occupancyAmenityRepository).save(any());
    }

    @Test
    void applyToOccupancy_skipsNonAmenitySpaceTypes() {
        occupancy.getSpace().setType(SpaceType.RENTAL);
        occupancyAmenityService.applyToOccupancy(occupancy, List.of(dto("WIFI", "WiFi")), null);
        verify(occupancyAmenityRepository, org.mockito.Mockito.never()).deleteByOccupancyId(any());
    }

    private static AmenityAssignmentDto dto(String code, String label) {
        AmenityAssignmentDto dto = new AmenityAssignmentDto();
        dto.setCode(code);
        dto.setLabel(label);
        return dto;
    }
}
