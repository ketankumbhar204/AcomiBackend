package com.acomi.acomi_backend.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.mess.application.service.MessRegistrationService;
import com.acomi.acomi_backend.mess.domain.model.MessRegistrationSource;
import com.acomi.acomi_backend.mess.domain.model.MessRegistrationStatus;
import com.acomi.acomi_backend.mess.infrastructure.persistence.entity.MessRegistrationEntity;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.property.application.service.PropertyRegistrationService;
import com.acomi.acomi_backend.property.domain.model.PriceBasis;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationSource;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationStatus;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.acomi.acomi_backend.registration.api.dto.request.AdminUpdateRegistrationContactRequest;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminRegistrationContactServiceTest {

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
    void updatePropertyContact_addsAlternate() {
        UUID id = UUID.randomUUID();
        PropertyRegistrationEntity entity = propertyLead(id);
        when(propertyRegistrationService.requireEntity(id)).thenReturn(entity);
        when(propertyRegistrationRepository.save(any(PropertyRegistrationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdminUpdateRegistrationContactRequest request = new AdminUpdateRegistrationContactRequest();
        request.setAlternateMobileNumber("9123456780");

        var detail = adminPropertyRegistrationService.updateContact(id, request);

        assertThat(entity.getMobileNumber()).isEqualTo("9876543210");
        assertThat(entity.getAlternateMobileNumber()).isEqualTo("9123456780");
        assertThat(detail.getAlternateMobileNumber()).isEqualTo("9123456780");
        verify(propertyRegistrationRepository).save(entity);
    }

    @Test
    void updatePropertyContact_clearsAlternateWhenBlank() {
        UUID id = UUID.randomUUID();
        PropertyRegistrationEntity entity = propertyLead(id);
        entity.setAlternateMobileNumber("9123456780");
        when(propertyRegistrationService.requireEntity(id)).thenReturn(entity);
        when(propertyRegistrationRepository.save(any(PropertyRegistrationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdminUpdateRegistrationContactRequest request = new AdminUpdateRegistrationContactRequest();
        request.setAlternateMobileNumber("  ");

        var detail = adminPropertyRegistrationService.updateContact(id, request);

        assertThat(entity.getAlternateMobileNumber()).isNull();
        assertThat(detail.getAlternateMobileNumber()).isNull();
    }

    @Test
    void updatePropertyContact_rejectsAlternateEqualToPrimary() {
        UUID id = UUID.randomUUID();
        PropertyRegistrationEntity entity = propertyLead(id);
        when(propertyRegistrationService.requireEntity(id)).thenReturn(entity);

        AdminUpdateRegistrationContactRequest request = new AdminUpdateRegistrationContactRequest();
        request.setAlternateMobileNumber("9876543210");

        assertThatThrownBy(() -> adminPropertyRegistrationService.updateContact(id, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("different from the primary");
    }

    @Test
    void updateMessContact_addsAndClearsAlternate() {
        UUID id = UUID.randomUUID();
        MessRegistrationEntity entity = messLead(id);
        when(messRegistrationService.requireEntity(id)).thenReturn(entity);
        when(messRegistrationRepository.save(any(MessRegistrationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdminUpdateRegistrationContactRequest add = new AdminUpdateRegistrationContactRequest();
        add.setAlternateMobileNumber("9123456780");
        adminMessRegistrationService.updateContact(id, add);
        assertThat(entity.getAlternateMobileNumber()).isEqualTo("9123456780");

        AdminUpdateRegistrationContactRequest clear = new AdminUpdateRegistrationContactRequest();
        clear.setAlternateMobileNumber(null);
        var detail = adminMessRegistrationService.updateContact(id, clear);
        assertThat(entity.getAlternateMobileNumber()).isNull();
        assertThat(detail.getAlternateMobileNumber()).isNull();
    }

    private static PropertyRegistrationEntity propertyLead(UUID id) {
        PropertyRegistrationEntity entity = PropertyRegistrationEntity.builder()
                .reference("PR-2026-000001")
                .propertyType(SpaceType.PG)
                .propertyName("Sunrise PG")
                .ownerName("Owner")
                .mobileNumber("9876543210")
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

    private static MessRegistrationEntity messLead(UUID id) {
        MessRegistrationEntity entity = MessRegistrationEntity.builder()
                .reference("MR-2026-000001")
                .messName("Sunrise Mess")
                .ownerName("Owner")
                .mobileNumber("9876543210")
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
