package com.acomi.acomi_backend.admin.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.mess.application.service.MessRegistrationService;
import com.acomi.acomi_backend.mess.domain.model.MessRegistrationSource;
import com.acomi.acomi_backend.mess.domain.model.MessRegistrationStatus;
import com.acomi.acomi_backend.mess.infrastructure.persistence.entity.MessRegistrationEntity;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.property.application.service.PropertyRegistrationService;
import com.acomi.acomi_backend.property.domain.model.PriceBasis;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationSource;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationStatus;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationAmenityEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AdminRegistrationDeleteServiceTest {

    @Mock
    private PropertyRegistrationRepository propertyRegistrationRepository;

    @Mock
    private PropertyRegistrationService propertyRegistrationService;

    @Mock
    private MessRegistrationRepository messRegistrationRepository;

    @Mock
    private MessRegistrationService messRegistrationService;

    @Mock
    private com.acomi.acomi_backend.address.application.service.SavedAddressService savedAddressService;

    private AdminPropertyRegistrationService adminPropertyRegistrationService;
    private AdminMessRegistrationService adminMessRegistrationService;

    @BeforeEach
    void setUp() {
        adminPropertyRegistrationService =
                new AdminPropertyRegistrationService(
                        propertyRegistrationRepository, propertyRegistrationService, savedAddressService);
        adminMessRegistrationService =
                new AdminMessRegistrationService(
                        messRegistrationRepository, messRegistrationService, savedAddressService);
    }

    @Test
    void deletePropertyRegistration_deletesExistingLead() {
        UUID id = UUID.randomUUID();
        PropertyRegistrationEntity entity = propertyLead(id, SpaceType.PG, "Sunrise PG", "9876543210");

        when(propertyRegistrationService.requireEntity(id)).thenReturn(entity);

        adminPropertyRegistrationService.delete(id);

        verify(propertyRegistrationRepository).delete(entity);
    }

    @Test
    void deletePropertyRegistration_throwsNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(propertyRegistrationService.requireEntity(id))
                .thenThrow(new ResourceNotFoundException("Property registration", "id", id));

        assertThatThrownBy(() -> adminPropertyRegistrationService.delete(id))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(propertyRegistrationRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deletePropertyRegistration_rejectsConvertedLead() {
        UUID id = UUID.randomUUID();
        PropertyRegistrationEntity entity = propertyLead(id, SpaceType.PG, "Sunrise PG", "9876543210");
        entity.setStatus(PropertyRegistrationStatus.CONVERTED);
        entity.setConvertedSpaceId(UUID.randomUUID());

        when(propertyRegistrationService.requireEntity(id)).thenReturn(entity);

        assertThatThrownBy(() -> adminPropertyRegistrationService.delete(id))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(propertyRegistrationRepository, never()).delete(entity);
    }

    @Test
    void deletePropertyRegistration_doesNotAffectOtherLeadWithSameMobile() {
        UUID targetId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        PropertyRegistrationEntity target = propertyLead(targetId, SpaceType.PG, "Sunrise PG", "9876543210");
        PropertyRegistrationEntity other = propertyLead(otherId, SpaceType.HOSTEL, "Sunrise Hostel", "9876543210");

        when(propertyRegistrationService.requireEntity(targetId)).thenReturn(target);

        adminPropertyRegistrationService.delete(targetId);

        verify(propertyRegistrationRepository).delete(target);
        verify(propertyRegistrationRepository, never()).delete(other);
    }

    @Test
    void deletePropertyRegistration_doesNotAffectMessWithSameMobile() {
        UUID propertyId = UUID.randomUUID();
        PropertyRegistrationEntity property = propertyLead(propertyId, SpaceType.PG, "Sunrise PG", "9876543210");

        when(propertyRegistrationService.requireEntity(propertyId)).thenReturn(property);

        adminPropertyRegistrationService.delete(propertyId);

        verify(propertyRegistrationRepository).delete(property);
        verify(messRegistrationRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deletePropertyRegistration_deletesLeadWithAmenities() {
        UUID id = UUID.randomUUID();
        PropertyRegistrationEntity entity = propertyLead(id, SpaceType.PG, "Sunrise PG", "9876543210");
        PropertyRegistrationAmenityEntity amenity = PropertyRegistrationAmenityEntity.builder()
                .amenityCode("WIFI")
                .displayOrder(0)
                .build();
        entity.addAmenity(amenity);

        when(propertyRegistrationService.requireEntity(id)).thenReturn(entity);

        adminPropertyRegistrationService.delete(id);

        verify(propertyRegistrationRepository).delete(entity);
    }

    @Test
    void deleteMessRegistration_deletesExistingLead() {
        UUID id = UUID.randomUUID();
        MessRegistrationEntity entity = messLead(id, "Sunrise Mess", "9876543210");

        when(messRegistrationService.requireEntity(id)).thenReturn(entity);

        adminMessRegistrationService.delete(id);

        verify(messRegistrationRepository).delete(entity);
    }

    @Test
    void deleteMessRegistration_throwsNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(messRegistrationService.requireEntity(id))
                .thenThrow(new ResourceNotFoundException("Mess registration", "id", id));

        assertThatThrownBy(() -> adminMessRegistrationService.delete(id))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(messRegistrationRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteMessRegistration_rejectsConvertedLead() {
        UUID id = UUID.randomUUID();
        MessRegistrationEntity entity = messLead(id, "Sunrise Mess", "9876543210");
        entity.setStatus(MessRegistrationStatus.CONVERTED);
        entity.setConvertedSpaceId(UUID.randomUUID());

        when(messRegistrationService.requireEntity(id)).thenReturn(entity);

        assertThatThrownBy(() -> adminMessRegistrationService.delete(id))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(messRegistrationRepository, never()).delete(entity);
    }

    private static PropertyRegistrationEntity propertyLead(UUID id, SpaceType type, String name, String mobile) {
        PropertyRegistrationEntity entity = PropertyRegistrationEntity.builder()
                .reference("PR-2026-000001")
                .propertyType(type)
                .propertyName(name)
                .ownerName("Owner")
                .mobileNumber(mobile)
                .addressLine("Main Road")
                .city("Pune")
                .state("Maharashtra")
                .pincode("411001")
                .startingPrice(new BigDecimal("8000"))
                .priceBasis(PriceBasis.PER_BED)
                .status(PropertyRegistrationStatus.PENDING)
                .source(PropertyRegistrationSource.ADMIN)
                .build();
        entity.setId(id);
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }

    private static MessRegistrationEntity messLead(UUID id, String name, String mobile) {
        MessRegistrationEntity entity = MessRegistrationEntity.builder()
                .reference("MR-2026-000001")
                .messName(name)
                .ownerName("Owner")
                .mobileNumber(mobile)
                .addressLine("Main Road")
                .city("Pune")
                .state("Maharashtra")
                .pincode("411001")
                .monthlyPrice(new BigDecimal("3000"))
                .mealPrice(new BigDecimal("80"))
                .status(MessRegistrationStatus.PENDING)
                .source(MessRegistrationSource.ADMIN)
                .build();
        entity.setId(id);
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }
}
