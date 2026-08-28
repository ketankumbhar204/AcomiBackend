package com.acomi.acomi_backend.property.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.auth.application.service.OtpService;
import com.acomi.acomi_backend.auth.domain.model.OtpPurpose;
import com.acomi.acomi_backend.property.api.dto.request.AdminCreatePropertyRegistrationRequest;
import com.acomi.acomi_backend.property.api.dto.request.CreatePropertyRegistrationRequest;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationSource;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationStatus;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.acomi.acomi_backend.registration.domain.model.RegistrationClaimVia;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PropertyRegistrationServiceClaimTest {

    @Mock
    private PropertyRegistrationRepository propertyRegistrationRepository;

    @Mock
    private OtpService otpService;

    private PropertyRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new PropertyRegistrationService(propertyRegistrationRepository, otpService);
    }

    @Test
    void publicRegister_claimsSingleMatchingAdminLead() {
        CreatePropertyRegistrationRequest request = publicRequest(SpaceType.PG, "Sunrise PG", "9876543210");
        PropertyRegistrationEntity adminLead = adminLead(SpaceType.PG, "Sunrise PG", "9876543210");

        when(propertyRegistrationRepository.findUnclaimedAdminLeads("9876543210", SpaceType.PG, "Sunrise PG"))
                .thenReturn(List.of(adminLead));
        when(propertyRegistrationRepository.existsLikelyDuplicate("9876543210", "411001", "Sunrise PG"))
                .thenReturn(false);
        when(propertyRegistrationRepository.save(any(PropertyRegistrationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.registerPublic(request, "127.0.0.1");

        verify(otpService)
                .consumeVerificationToken("9876543210", "token", OtpPurpose.PROPERTY_REGISTRATION);
        verify(propertyRegistrationRepository, never()).nextReferenceNumber();

        ArgumentCaptor<PropertyRegistrationEntity> captor = ArgumentCaptor.forClass(PropertyRegistrationEntity.class);
        verify(propertyRegistrationRepository).save(captor.capture());
        PropertyRegistrationEntity saved = captor.getValue();
        assertThat(saved.getSource()).isEqualTo(PropertyRegistrationSource.ADMIN);
        assertThat(saved.getClaimedVia()).isEqualTo(RegistrationClaimVia.PUBLIC_WEBSITE);
        assertThat(saved.getMobileVerifiedAt()).isNotNull();
        assertThat(saved.getOwnerName()).isEqualTo("Ketan");
    }

    @Test
    void publicRegister_differentTypeDoesNotClaimAdminLead() {
        CreatePropertyRegistrationRequest request =
                publicRequest(SpaceType.HOSTEL, "Sunrise Hostel", "9876543210");

        when(propertyRegistrationRepository.findUnclaimedAdminLeads("9876543210", SpaceType.HOSTEL, "Sunrise Hostel"))
                .thenReturn(Collections.emptyList());
        when(propertyRegistrationRepository.existsLikelyDuplicate("9876543210", "411001", "Sunrise Hostel"))
                .thenReturn(false);
        when(propertyRegistrationRepository.nextReferenceNumber()).thenReturn(42L);
        when(propertyRegistrationRepository.save(any(PropertyRegistrationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.registerPublic(request, "127.0.0.1");

        ArgumentCaptor<PropertyRegistrationEntity> captor = ArgumentCaptor.forClass(PropertyRegistrationEntity.class);
        verify(propertyRegistrationRepository).save(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo(PropertyRegistrationSource.PUBLIC_WEBSITE);
        assertThat(captor.getValue().getPropertyType()).isEqualTo(SpaceType.HOSTEL);
    }

    @Test
    void publicRegister_ambiguousAdminLeadsCreatesNewRow() {
        CreatePropertyRegistrationRequest request = publicRequest(SpaceType.PG, "Sunrise PG", "9876543210");

        when(propertyRegistrationRepository.findUnclaimedAdminLeads("9876543210", SpaceType.PG, "Sunrise PG"))
                .thenReturn(List.of(adminLead(SpaceType.PG, "Sunrise PG", "9876543210"), adminLead(SpaceType.PG, "Sunrise PG", "9876543210")));
        when(propertyRegistrationRepository.existsLikelyDuplicate("9876543210", "411001", "Sunrise PG"))
                .thenReturn(false);
        when(propertyRegistrationRepository.nextReferenceNumber()).thenReturn(7L);
        when(propertyRegistrationRepository.save(any(PropertyRegistrationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.registerPublic(request, "127.0.0.1");

        ArgumentCaptor<PropertyRegistrationEntity> captor = ArgumentCaptor.forClass(PropertyRegistrationEntity.class);
        verify(propertyRegistrationRepository).save(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo(PropertyRegistrationSource.PUBLIC_WEBSITE);
    }

    @Test
    void adminRegister_setsAdminSourceWithoutOtp() {
        AdminCreatePropertyRegistrationRequest request = new AdminCreatePropertyRegistrationRequest();
        request.setPropertyType(SpaceType.PG);
        request.setPropertyName("Sunrise PG");
        request.setOwnerName("Ketan");
        request.setMobileNumber("9876543210");
        request.setAddressLine("Main Road");
        request.setCity("Pune");
        request.setState("Maharashtra");
        request.setPincode("411001");
        request.setStartingPrice(new BigDecimal("8000"));

        when(propertyRegistrationRepository.existsLikelyDuplicate("9876543210", "411001", "Sunrise PG"))
                .thenReturn(false);
        when(propertyRegistrationRepository.nextReferenceNumber()).thenReturn(1L);
        when(propertyRegistrationRepository.save(any(PropertyRegistrationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.registerAdmin(request);

        verify(otpService, never()).consumeVerificationToken(any(), any(), any());
        ArgumentCaptor<PropertyRegistrationEntity> captor = ArgumentCaptor.forClass(PropertyRegistrationEntity.class);
        verify(propertyRegistrationRepository).save(captor.capture());
        PropertyRegistrationEntity saved = captor.getValue();
        assertThat(saved.getSource()).isEqualTo(PropertyRegistrationSource.ADMIN);
        assertThat(saved.getMobileVerifiedAt()).isNull();
        assertThat(saved.getClaimedAt()).isNull();
    }

    @Test
    void adminRegister_setsTestLeadWhenRequested() {
        AdminCreatePropertyRegistrationRequest request = new AdminCreatePropertyRegistrationRequest();
        request.setTestLead(true);

        when(propertyRegistrationRepository.existsLikelyDuplicate("6000000000", "110001", "Untitled property"))
                .thenReturn(false);
        when(propertyRegistrationRepository.nextReferenceNumber()).thenReturn(3L);
        when(propertyRegistrationRepository.save(any(PropertyRegistrationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.registerAdmin(request);

        ArgumentCaptor<PropertyRegistrationEntity> captor = ArgumentCaptor.forClass(PropertyRegistrationEntity.class);
        verify(propertyRegistrationRepository).save(captor.capture());
        assertThat(captor.getValue().isTestLead()).isTrue();
    }

    @Test
    void adminRegister_allowsEmptyFieldsWithDefaults() {
        AdminCreatePropertyRegistrationRequest request = new AdminCreatePropertyRegistrationRequest();

        when(propertyRegistrationRepository.existsLikelyDuplicate("6000000000", "110001", "Untitled property"))
                .thenReturn(false);
        when(propertyRegistrationRepository.nextReferenceNumber()).thenReturn(2L);
        when(propertyRegistrationRepository.save(any(PropertyRegistrationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.registerAdmin(request);

        ArgumentCaptor<PropertyRegistrationEntity> captor = ArgumentCaptor.forClass(PropertyRegistrationEntity.class);
        verify(propertyRegistrationRepository).save(captor.capture());
        PropertyRegistrationEntity saved = captor.getValue();
        assertThat(saved.getPropertyName()).isEqualTo("Untitled property");
        assertThat(saved.getPropertyType()).isEqualTo(SpaceType.PG);
        assertThat(saved.getMapUrl()).isNull();
    }

    private static CreatePropertyRegistrationRequest publicRequest(
            SpaceType type, String name, String mobile) {
        CreatePropertyRegistrationRequest request = new CreatePropertyRegistrationRequest();
        request.setPropertyType(type);
        request.setPropertyName(name);
        request.setOwnerName("Ketan");
        request.setMobileNumber(mobile);
        request.setVerificationToken("token");
        request.setAddressLine("Main Road");
        request.setCity("Pune");
        request.setState("Maharashtra");
        request.setPincode("411001");
        request.setStartingPrice(new BigDecimal("8000"));
        return request;
    }

    private static PropertyRegistrationEntity adminLead(SpaceType type, String name, String mobile) {
        PropertyRegistrationEntity entity = PropertyRegistrationEntity.builder()
                .reference("PR-2026-000001")
                .propertyType(type)
                .propertyName(name)
                .ownerName("Admin Entry")
                .mobileNumber(mobile)
                .addressLine("Old Address")
                .city("Pune")
                .state("Maharashtra")
                .pincode("411001")
                .startingPrice(new BigDecimal("7000"))
                .priceBasis(com.acomi.acomi_backend.property.domain.model.PriceBasis.PER_BED)
                .status(PropertyRegistrationStatus.PENDING)
                .source(PropertyRegistrationSource.ADMIN)
                .build();
        entity.setId(UUID.randomUUID());
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }
}
