package com.acomi.acomi_backend.space.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.space.api.dto.AmenityAssignmentDto;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceAmenityEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceAmenityRepository;
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
class SpaceAmenityServiceTest {

    @Mock
    private SpaceAmenityRepository spaceAmenityRepository;

    @InjectMocks
    private SpaceAmenityService spaceAmenityService;

    private SpaceEntity space;

    @BeforeEach
    void setUp() {
        space = SpaceEntity.builder().type(SpaceType.PG).build();
        space.setId(UUID.randomUUID());
    }

    @Test
    void supportsAmenities_onlyPgHostelCoLiving() {
        assertThat(SpaceAmenityService.supportsAmenities(SpaceType.PG)).isTrue();
        assertThat(SpaceAmenityService.supportsAmenities(SpaceType.HOSTEL)).isTrue();
        assertThat(SpaceAmenityService.supportsAmenities(SpaceType.CO_LIVING)).isTrue();
        assertThat(SpaceAmenityService.supportsAmenities(SpaceType.MESS)).isFalse();
        assertThat(SpaceAmenityService.supportsAmenities(SpaceType.RENTAL)).isFalse();
    }

    @Test
    void normalizeAssignments_deduplicatesAndUsesDefaultLabels() {
        AmenityAssignmentDto wifi = dto("WIFI", null);
        AmenityAssignmentDto wifiDup = dto("WIFI", "Ignored");
        AmenityAssignmentDto custom = dto("CUSTOM", "Gym Access");

        List<AmenityAssignmentDto> normalized =
                SpaceAmenityService.normalizeAssignments(List.of(wifi, wifiDup, custom));

        assertThat(normalized).hasSize(2);
        assertThat(normalized.get(0).getCode()).isEqualTo("WIFI");
        assertThat(normalized.get(0).getLabel()).isEqualTo("WiFi");
        assertThat(normalized.get(1).getLabel()).isEqualTo("Gym Access");
    }

    @Test
    void normalizeAssignments_rejectsUnknownCode() {
        assertThatThrownBy(() -> SpaceAmenityService.normalizeAssignments(List.of(dto("POOL", null))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Unknown amenity code");
    }

    @Test
    void assertSubsetOfSpaceAmenities_rejectsUnavailableAmenity() {
        List<AmenityAssignmentDto> spaceAmenities = List.of(dto("WIFI", null));
        List<AmenityAssignmentDto> requested = List.of(dto("PARKING", null));

        assertThatThrownBy(() -> SpaceAmenityService.assertSubsetOfSpaceAmenities(requested, spaceAmenities))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not available");
    }

    @Test
    void replaceForSpace_persistsNormalizedAmenities() {
        when(spaceAmenityRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        spaceAmenityService.replaceForSpace(
                space, List.of(dto("WIFI", null), dto("CUSTOM", "Rooftop Lounge")));

        ArgumentCaptor<SpaceAmenityEntity> captor = ArgumentCaptor.forClass(SpaceAmenityEntity.class);
        verify(spaceAmenityRepository).deleteBySpaceId(space.getId());
        verify(spaceAmenityRepository, org.mockito.Mockito.times(2)).save(captor.capture());

        assertThat(captor.getAllValues())
                .extracting(SpaceAmenityEntity::getAmenityCode)
                .containsExactly("WIFI", "CUSTOM");
    }

    @Test
    void replaceForSpace_skipsNonAmenitySpaceTypes() {
        space.setType(SpaceType.MESS);
        spaceAmenityService.replaceForSpace(space, List.of(dto("WIFI", null)));
        verify(spaceAmenityRepository, org.mockito.Mockito.never()).deleteBySpaceId(any());
    }

    private static AmenityAssignmentDto dto(String code, String label) {
        AmenityAssignmentDto dto = new AmenityAssignmentDto();
        dto.setCode(code);
        dto.setLabel(label);
        return dto;
    }
}
