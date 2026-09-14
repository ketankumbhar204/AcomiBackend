package com.acomi.acomi_backend.enquiry.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.property.domain.model.PriceBasis;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationAmenityEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.acomi.acomi_backend.registration.application.AdminLeadDefaults;
import com.acomi.acomi_backend.space.api.dto.AmenityAssignmentDto;
import com.acomi.acomi_backend.space.application.service.SpaceAmenityService;
import com.acomi.acomi_backend.space.domain.model.GenderPolicy;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EnquiryListingDetailsResolverTest {

    @Mock
    private PropertyRegistrationRepository propertyRegistrationRepository;

    @Mock
    private MessRegistrationRepository messRegistrationRepository;

    @Mock
    private SpaceAmenityService spaceAmenityService;

    private EnquiryListingDetailsResolver resolver;
    private UUID spaceId;
    private SpaceEntity space;

    @BeforeEach
    void setUp() {
        resolver = new EnquiryListingDetailsResolver(
                propertyRegistrationRepository, messRegistrationRepository, spaceAmenityService);
        spaceId = UUID.randomUUID();
        space = SpaceEntity.builder()
                .name("Lovely Home's PG 3")
                .type(SpaceType.PG)
                .address(AdminLeadDefaults.PLACEHOLDER_PINCODE)
                .isActive(true)
                .discoverable(true)
                .build();
        space.setId(spaceId);
    }

    @Test
    void placeholderAddressAndPincodeBecomeNull() {
        PropertyRegistrationEntity registration = PropertyRegistrationEntity.builder()
                .propertyName("Lovely Home's PG 3")
                .addressLine(AdminLeadDefaults.PLACEHOLDER_ADDRESS)
                .city(AdminLeadDefaults.PLACEHOLDER_ADDRESS)
                .state(AdminLeadDefaults.PLACEHOLDER_ADDRESS)
                .pincode(AdminLeadDefaults.PLACEHOLDER_PINCODE)
                .convertedSpaceId(spaceId)
                .build();
        when(propertyRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.of(registration));
        when(spaceAmenityService.getForSpace(spaceId)).thenReturn(List.of());

        EnquiryListingDetails details = resolver.resolve(space);

        assertThat(details.addressLine()).isNull();
        assertThat(details.city()).isNull();
        assertThat(details.pincode()).isNull();
        assertThat(details.location()).isNull();
        assertThat(details.mapUrl()).isNull();
    }

    @Test
    void realDelhiPincodeAndMapLinkAreKept() {
        AmenityAssignmentDto wifi = new AmenityAssignmentDto();
        wifi.setCode("WIFI");
        wifi.setLabel("WiFi");
        PropertyRegistrationEntity registration = PropertyRegistrationEntity.builder()
                .propertyName("Sunrise PG")
                .description("Near metro")
                .addressLine("12 MG Road")
                .city("New Delhi")
                .state("Delhi")
                .pincode("110001")
                .mapUrl("https://maps.google.com/?q=28.61,77.20")
                .latitude(new BigDecimal("28.6100000"))
                .longitude(new BigDecimal("77.2000000"))
                .startingPrice(new BigDecimal("8500.00"))
                .priceBasis(PriceBasis.PER_BED)
                .capacityEstimate(12)
                .genderPolicy(GenderPolicy.MALE)
                .foodIncludedListing(true)
                .sharingNotes("Double sharing")
                .convertedSpaceId(spaceId)
                .build();
        registration.addAmenity(PropertyRegistrationAmenityEntity.builder()
                .amenityCode("WIFI")
                .displayOrder(0)
                .build());
        when(propertyRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.of(registration));
        when(spaceAmenityService.getForSpace(spaceId)).thenReturn(List.of(wifi));

        EnquiryListingDetails details = resolver.resolve(space);

        assertThat(details.addressLine()).isEqualTo("12 MG Road");
        assertThat(details.pincode()).isEqualTo("110001");
        assertThat(details.location()).isEqualTo("12 MG Road, New Delhi, Delhi, 110001");
        assertThat(details.mapUrl()).isEqualTo("https://maps.google.com/?q=28.61,77.20");
        assertThat(details.startingPrice()).isEqualTo("INR 8500");
        assertThat(details.priceBasis()).isEqualTo("Per bed");
        assertThat(details.genderPolicy()).isEqualTo("Male");
        assertThat(details.foodIncluded()).isEqualTo("Yes");
        assertThat(details.amenities()).isEqualTo("WiFi");
    }

    @Test
    void missingMapUrlFallsBackToCoordinates() {
        PropertyRegistrationEntity registration = PropertyRegistrationEntity.builder()
                .propertyName("Sunrise PG")
                .addressLine("Wakad")
                .city("Pune")
                .state("Maharashtra")
                .pincode("411057")
                .latitude(new BigDecimal("18.6052262"))
                .longitude(new BigDecimal("73.7236231"))
                .convertedSpaceId(spaceId)
                .build();
        when(propertyRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.of(registration));
        when(spaceAmenityService.getForSpace(spaceId)).thenReturn(List.of());

        EnquiryListingDetails details = resolver.resolve(space);

        assertThat(details.mapUrl()).isEqualTo("https://maps.google.com/?q=18.6052262,73.7236231");
        assertThat(details.coordinates()).isEqualTo("18.6052262, 73.7236231");
    }
}
