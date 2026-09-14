package com.acomi.acomi_backend.space.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.common.web.PagedResponse;
import com.acomi.acomi_backend.config.discovery.DiscoveryProperties;
import com.acomi.acomi_backend.member.domain.model.MembershipStatus;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.mess.domain.model.MessRegistrationSource;
import com.acomi.acomi_backend.mess.domain.model.MessRegistrationStatus;
import com.acomi.acomi_backend.mess.infrastructure.persistence.entity.MessRegistrationEntity;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.property.domain.model.PriceBasis;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationSource;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationStatus;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.acomi.acomi_backend.space.api.dto.AmenityAssignmentDto;
import com.acomi.acomi_backend.space.api.dto.response.DiscoverSpaceCardResponse;
import com.acomi.acomi_backend.space.api.dto.response.DiscoverSpaceDetailResponse;
import com.acomi.acomi_backend.space.domain.model.GenderPolicy;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class SpaceDiscoverServiceTest {

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private SpaceAmenityService spaceAmenityService;

    @Mock
    private SpaceMembershipRepository spaceMembershipRepository;

    @Mock
    private PropertyRegistrationRepository propertyRegistrationRepository;

    @Mock
    private MessRegistrationRepository messRegistrationRepository;

    @Mock
    private DiscoveryProperties discoveryProperties;

    @InjectMocks
    private SpaceDiscoverService spaceDiscoverService;

    private UUID callerId;
    private UUID activeSpaceId;
    private SpaceEntity activeSpace;

    @BeforeEach
    void setUp() {
        callerId = UUID.randomUUID();
        activeSpaceId = UUID.randomUUID();
        activeSpace = SpaceEntity.builder()
                .name("Sunrise PG")
                .type(SpaceType.PG)
                .address("12 MG Road")
                .isActive(true)
                .foodIncludedInRent(true)
                .genderPolicy(GenderPolicy.MIXED)
                .build();
        activeSpace.setId(activeSpaceId);
        when(discoveryProperties.isIncludeTestSpaces()).thenReturn(false);
    }

    @Test
    void discover_excludesInactiveViaRepositoryQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        when(spaceRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(activeSpace), pageable, 1));
        when(spaceAmenityService.getForSpaces(List.of(activeSpaceId))).thenReturn(Map.of());
        when(spaceMembershipRepository.findActiveSpaceIdsByUserIdAndSpaceIdIn(
                        eq(callerId), eq(List.of(activeSpaceId))))
                .thenReturn(List.of());
        when(propertyRegistrationRepository.findTestLeadConvertedSpaceIds(List.of(activeSpaceId)))
                .thenReturn(List.of());
        when(messRegistrationRepository.findTestLeadConvertedSpaceIds(List.of(activeSpaceId)))
                .thenReturn(List.of());

        PagedResponse<DiscoverSpaceCardResponse> response =
                spaceDiscoverService.discover(callerId, null, null, "newest", pageable);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(spaceRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getSpaceId()).isEqualTo(activeSpaceId);
        assertThat(response.getContent().get(0).isAlreadyMember()).isFalse();
    }

    @Test
    void discover_appliesTypeFilter() {
        Pageable pageable = PageRequest.of(0, 20);
        when(spaceRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));
        when(spaceAmenityService.getForSpaces(List.of())).thenReturn(Map.of());

        PagedResponse<DiscoverSpaceCardResponse> response =
                spaceDiscoverService.discover(callerId, "  ", SpaceType.MESS, "newest", pageable);

        verify(spaceRepository).findAll(any(Specification.class), any(Pageable.class));
        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isZero();
    }

    @Test
    void discover_searchesByName() {
        Pageable pageable = PageRequest.of(0, 20);
        when(spaceRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(activeSpace), pageable, 1));
        AmenityAssignmentDto wifi = amenity("WIFI", "WiFi");
        when(spaceAmenityService.getForSpaces(List.of(activeSpaceId)))
                .thenReturn(Map.of(activeSpaceId, List.of(wifi)));
        when(spaceMembershipRepository.findActiveSpaceIdsByUserIdAndSpaceIdIn(
                        eq(callerId), eq(List.of(activeSpaceId))))
                .thenReturn(List.of());
        when(propertyRegistrationRepository.findTestLeadConvertedSpaceIds(List.of(activeSpaceId)))
                .thenReturn(List.of());
        when(messRegistrationRepository.findTestLeadConvertedSpaceIds(List.of(activeSpaceId)))
                .thenReturn(List.of());

        PagedResponse<DiscoverSpaceCardResponse> response =
                spaceDiscoverService.discover(callerId, " sunrise ", null, "newest", pageable);

        verify(spaceRepository).findAll(any(Specification.class), any(Pageable.class));
        DiscoverSpaceCardResponse card = response.getContent().get(0);
        assertThat(card.getName()).isEqualTo("Sunrise PG");
        assertThat(card.getAmenityCodes()).containsExactly("WIFI");
        assertThat(card.getAmenityLabels()).containsExactly("WiFi");
        assertThat(card.isFoodIncludedInRent()).isTrue();
        assertThat(card.getGenderPolicy()).isEqualTo(GenderPolicy.MIXED);
    }

    @Test
    void discover_marksAlreadyMemberTrueWhenActiveMembershipExists() {
        Pageable pageable = PageRequest.of(0, 20);
        when(spaceRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(activeSpace), pageable, 1));
        when(spaceAmenityService.getForSpaces(List.of(activeSpaceId))).thenReturn(Map.of());
        when(spaceMembershipRepository.findActiveSpaceIdsByUserIdAndSpaceIdIn(
                        eq(callerId), eq(List.of(activeSpaceId))))
                .thenReturn(List.of(activeSpaceId));
        when(propertyRegistrationRepository.findTestLeadConvertedSpaceIds(List.of(activeSpaceId)))
                .thenReturn(List.of());
        when(messRegistrationRepository.findTestLeadConvertedSpaceIds(List.of(activeSpaceId)))
                .thenReturn(List.of());

        PagedResponse<DiscoverSpaceCardResponse> response =
                spaceDiscoverService.discover(callerId, null, null, "newest", pageable);

        assertThat(response.getContent().get(0).isAlreadyMember()).isTrue();
    }

    @Test
    void discover_anonymousCallerSkipsMembershipLookup() {
        Pageable pageable = PageRequest.of(0, 20);
        when(spaceRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(activeSpace), pageable, 1));
        when(spaceAmenityService.getForSpaces(List.of(activeSpaceId))).thenReturn(Map.of());
        when(propertyRegistrationRepository.findTestLeadConvertedSpaceIds(List.of(activeSpaceId)))
                .thenReturn(List.of());
        when(messRegistrationRepository.findTestLeadConvertedSpaceIds(List.of(activeSpaceId)))
                .thenReturn(List.of());

        PagedResponse<DiscoverSpaceCardResponse> response =
                spaceDiscoverService.discover(null, null, null, "newest", pageable);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).isAlreadyMember()).isFalse();
        verify(spaceMembershipRepository, org.mockito.Mockito.never())
                .findActiveSpaceIdsByUserIdAndSpaceIdIn(any(), any());
    }

    @Test
    void getDetail_anonymousCallerIsNotMemberOrOwner() {
        when(spaceRepository.findByIdAndIsActiveTrueAndDiscoverableTrue(activeSpaceId))
                .thenReturn(Optional.of(activeSpace));
        when(spaceAmenityService.getForSpace(activeSpaceId)).thenReturn(List.of());
        when(propertyRegistrationRepository.findByConvertedSpaceId(activeSpaceId))
                .thenReturn(Optional.empty());
        when(messRegistrationRepository.findByConvertedSpaceId(activeSpaceId))
                .thenReturn(Optional.empty());

        DiscoverSpaceDetailResponse detail = spaceDiscoverService.getDetail(null, activeSpaceId);

        assertThat(detail.isAlreadyMember()).isFalse();
        assertThat(detail.isOwnedByCurrentUser()).isFalse();
        verify(spaceMembershipRepository, org.mockito.Mockito.never())
                .existsByUserIdAndSpaceIdAndStatus(any(), any(), any());
        verify(spaceRepository, org.mockito.Mockito.never()).existsByIdAndOwnerIdAndIsActiveTrue(any(), any());
    }

    @Test
    void discover_clampsPageSizeToMax50() {
        Pageable pageable = PageRequest.of(0, 100);
        when(spaceRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));
        when(spaceAmenityService.getForSpaces(List.of())).thenReturn(Map.of());

        spaceDiscoverService.discover(callerId, null, null, "newest", pageable);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(spaceRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void getDetail_returnsAmenitiesAndMembershipFlag() {
        AmenityAssignmentDto wifi = amenity("WIFI", "WiFi");
        when(spaceRepository.findByIdAndIsActiveTrueAndDiscoverableTrue(activeSpaceId))
                .thenReturn(Optional.of(activeSpace));
        when(spaceAmenityService.getForSpace(activeSpaceId)).thenReturn(List.of(wifi));
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndStatus(
                        callerId, activeSpaceId, MembershipStatus.ACTIVE))
                .thenReturn(false);
        when(propertyRegistrationRepository.findByConvertedSpaceId(activeSpaceId))
                .thenReturn(Optional.empty());
        when(messRegistrationRepository.findByConvertedSpaceId(activeSpaceId))
                .thenReturn(Optional.empty());
        when(spaceRepository.existsByIdAndOwnerIdAndIsActiveTrue(activeSpaceId, callerId))
                .thenReturn(false);

        DiscoverSpaceDetailResponse detail = spaceDiscoverService.getDetail(callerId, activeSpaceId);

        assertThat(detail.getSpaceId()).isEqualTo(activeSpaceId);
        assertThat(detail.getAmenities()).hasSize(1);
        assertThat(detail.getAmenityCodes()).containsExactly("WIFI");
        assertThat(detail.isAlreadyMember()).isFalse();
        assertThat(detail.isOwnedByCurrentUser()).isFalse();
        assertThat(detail.getStartingPrice()).isNull();
        assertThat(detail.getSharingNotes()).isNull();
        assertThat(detail.getDescription()).isNull();
    }

    @Test
    void getDetail_mapsSafeListingFieldsFromConvertedPropertyAndOmitsOwnerContact() throws Exception {
        activeSpace.setLatitude(new BigDecimal("18.6052262"));
        activeSpace.setLongitude(new BigDecimal("73.7236231"));
        PropertyRegistrationEntity property = PropertyRegistrationEntity.builder()
                .propertyType(SpaceType.PG)
                .propertyName("Sunrise PG")
                .ownerName("Private Owner")
                .mobileNumber("9991110001")
                .alternateMobileNumber("9991110002")
                .additionalMobileNumber("9991110003")
                .mobileVerifiedAt(LocalDateTime.now())
                .description("A quiet paying-guest stay near the IT parks.")
                .addressLine("12, Datta Mandir Road")
                .city("Wakad")
                .state("Maharashtra")
                .pincode("411057")
                .mapUrl("https://maps.google.com/?q=18.6052262,73.7236231")
                .startingPrice(new BigDecimal("8500.00"))
                .priceBasis(PriceBasis.PER_BED)
                .status(PropertyRegistrationStatus.CONVERTED)
                .source(PropertyRegistrationSource.ADMIN)
                .sharingNotes("1, 2 & 3 Sharing")
                .reviewNotes("Internal follow-up")
                .build();
        when(spaceRepository.findByIdAndIsActiveTrueAndDiscoverableTrue(activeSpaceId))
                .thenReturn(Optional.of(activeSpace));
        when(spaceAmenityService.getForSpace(activeSpaceId)).thenReturn(List.of());
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndStatus(
                        callerId, activeSpaceId, MembershipStatus.ACTIVE))
                .thenReturn(false);
        when(propertyRegistrationRepository.findByConvertedSpaceId(activeSpaceId))
                .thenReturn(Optional.of(property));
        when(messRegistrationRepository.findByConvertedSpaceId(activeSpaceId)).thenReturn(Optional.empty());
        when(spaceRepository.existsByIdAndOwnerIdAndIsActiveTrue(activeSpaceId, callerId))
                .thenReturn(false);

        DiscoverSpaceDetailResponse detail = spaceDiscoverService.getDetail(callerId, activeSpaceId);

        assertThat(detail.getName()).isEqualTo("Sunrise PG");
        assertThat(detail.getType()).isEqualTo(SpaceType.PG);
        assertThat(detail.getAddressLine()).isEqualTo("12, Datta Mandir Road");
        assertThat(detail.getCity()).isEqualTo("Wakad");
        assertThat(detail.getState()).isEqualTo("Maharashtra");
        assertThat(detail.getPincode()).isEqualTo("411057");
        assertThat(detail.getLatitude()).isEqualByComparingTo("18.6052262");
        assertThat(detail.getLongitude()).isEqualByComparingTo("73.7236231");
        assertThat(detail.getStartingPrice()).isEqualByComparingTo("8500.00");
        assertThat(detail.getPriceBasis()).isEqualTo(PriceBasis.PER_BED);
        assertThat(detail.getSharingNotes()).isEqualTo("1, 2 & 3 Sharing");
        assertThat(detail.getDescription()).isEqualTo("A quiet paying-guest stay near the IT parks.");
        assertThat(detail.getMapUrl()).isEqualTo("https://maps.google.com/?q=18.6052262,73.7236231");
        assertThat(detail.getGenderPolicy()).isEqualTo(GenderPolicy.MIXED);
        String json = new ObjectMapper().writeValueAsString(detail);
        assertThat(json).doesNotContain("9991110001");
        assertThat(json).doesNotContain("9991110002");
        assertThat(json).doesNotContain("9991110003");
        assertThat(json).doesNotContain("Private Owner");
        assertThat(json).doesNotContain("Internal follow-up");
        assertThat(json).doesNotContain("ownerName");
        assertThat(json).doesNotContain("mobileNumber");
    }

    @Test
    void getDetail_omitsZeroStartingPriceAndInvalidMapUrl() {
        PropertyRegistrationEntity property = PropertyRegistrationEntity.builder()
                .propertyType(SpaceType.PG)
                .propertyName("Sunrise PG")
                .ownerName("Private Owner")
                .mobileNumber("9991110001")
                .mobileVerifiedAt(LocalDateTime.now())
                .addressLine("—")
                .city("Wakad")
                .state("—")
                .pincode("411057")
                .mapUrl("javascript:alert(1)")
                .startingPrice(BigDecimal.ZERO)
                .priceBasis(PriceBasis.PER_BED)
                .status(PropertyRegistrationStatus.CONVERTED)
                .source(PropertyRegistrationSource.ADMIN)
                .build();
        when(spaceRepository.findByIdAndIsActiveTrueAndDiscoverableTrue(activeSpaceId))
                .thenReturn(Optional.of(activeSpace));
        when(spaceAmenityService.getForSpace(activeSpaceId)).thenReturn(List.of());
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndStatus(
                        callerId, activeSpaceId, MembershipStatus.ACTIVE))
                .thenReturn(false);
        when(propertyRegistrationRepository.findByConvertedSpaceId(activeSpaceId))
                .thenReturn(Optional.of(property));
        when(messRegistrationRepository.findByConvertedSpaceId(activeSpaceId)).thenReturn(Optional.empty());
        when(spaceRepository.existsByIdAndOwnerIdAndIsActiveTrue(activeSpaceId, callerId))
                .thenReturn(false);

        DiscoverSpaceDetailResponse detail = spaceDiscoverService.getDetail(callerId, activeSpaceId);

        assertThat(detail.getStartingPrice()).isNull();
        assertThat(detail.getMapUrl()).isNull();
        assertThat(detail.getAddressLine()).isNull();
        assertThat(detail.getCity()).isEqualTo("Wakad");
        assertThat(detail.getState()).isNull();
        assertThat(detail.getPincode()).isEqualTo("411057");
    }

    @Test
    void getDetail_mapsSafeMessListingPrices() {
        MessRegistrationEntity mess = MessRegistrationEntity.builder()
                .messName("Campus Mess")
                .ownerName("Private Owner")
                .mobileNumber("9991110001")
                .mobileVerifiedAt(LocalDateTime.now())
                .addressLine("Hinjewadi")
                .city("Pune")
                .state("Maharashtra")
                .pincode("411057")
                .monthlyPrice(new BigDecimal("3500.00"))
                .mealPrice(new BigDecimal("80.00"))
                .status(MessRegistrationStatus.CONVERTED)
                .source(MessRegistrationSource.ADMIN)
                .build();
        when(spaceRepository.findByIdAndIsActiveTrueAndDiscoverableTrue(activeSpaceId))
                .thenReturn(Optional.of(activeSpace));
        when(spaceAmenityService.getForSpace(activeSpaceId)).thenReturn(List.of());
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndStatus(
                        callerId, activeSpaceId, MembershipStatus.ACTIVE))
                .thenReturn(false);
        when(propertyRegistrationRepository.findByConvertedSpaceId(activeSpaceId)).thenReturn(Optional.empty());
        when(messRegistrationRepository.findByConvertedSpaceId(activeSpaceId)).thenReturn(Optional.of(mess));
        when(spaceRepository.existsByIdAndOwnerIdAndIsActiveTrue(activeSpaceId, callerId))
                .thenReturn(false);

        DiscoverSpaceDetailResponse detail = spaceDiscoverService.getDetail(callerId, activeSpaceId);

        assertThat(detail.getMonthlyPrice()).isEqualByComparingTo("3500.00");
        assertThat(detail.getMealPrice()).isEqualByComparingTo("80.00");
        assertThat(detail.getStartingPrice()).isNull();
        assertThat(detail.getCity()).isEqualTo("Pune");
    }

    @Test
    void getDetail_marksOwnedByCurrentUserWhenCallerOwnsSpace() {
        when(spaceRepository.findByIdAndIsActiveTrueAndDiscoverableTrue(activeSpaceId))
                .thenReturn(Optional.of(activeSpace));
        when(spaceAmenityService.getForSpace(activeSpaceId)).thenReturn(List.of());
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndStatus(
                        callerId, activeSpaceId, MembershipStatus.ACTIVE))
                .thenReturn(true);
        when(propertyRegistrationRepository.findByConvertedSpaceId(activeSpaceId))
                .thenReturn(Optional.empty());
        when(messRegistrationRepository.findByConvertedSpaceId(activeSpaceId))
                .thenReturn(Optional.empty());
        when(spaceRepository.existsByIdAndOwnerIdAndIsActiveTrue(activeSpaceId, callerId))
                .thenReturn(true);

        DiscoverSpaceDetailResponse detail = spaceDiscoverService.getDetail(callerId, activeSpaceId);

        assertThat(detail.isOwnedByCurrentUser()).isTrue();
        assertThat(detail.isAlreadyMember()).isTrue();
    }

    @Test
    void getDetail_throwsNotFoundForInactiveOrMissingSpace() {
        UUID missingId = UUID.randomUUID();
        when(spaceRepository.findByIdAndIsActiveTrueAndDiscoverableTrue(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> spaceDiscoverService.getDetail(callerId, missingId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Space not found");
    }

    private static AmenityAssignmentDto amenity(String code, String label) {
        AmenityAssignmentDto dto = new AmenityAssignmentDto();
        dto.setCode(code);
        dto.setLabel(label);
        return dto;
    }
}
