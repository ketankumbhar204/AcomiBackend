package com.acomi.acomi_backend.admin.bulkimport.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.auth.application.service.OtpService;
import com.acomi.acomi_backend.mess.api.dto.request.AdminCreateMessRegistrationRequest;
import com.acomi.acomi_backend.mess.application.service.MessRegistrationService;
import com.acomi.acomi_backend.mess.infrastructure.persistence.entity.MessRegistrationEntity;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.property.api.dto.request.AdminCreatePropertyRegistrationRequest;
import com.acomi.acomi_backend.property.application.service.PropertyRegistrationService;
import com.acomi.acomi_backend.property.domain.model.PriceBasis;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.acomi.acomi_backend.registration.application.RegistrationOwnerLinker;
import com.acomi.acomi_backend.registration.application.RegistrationPhotoService;
import com.acomi.acomi_backend.space.application.service.SpaceService;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImportPricePersistenceTest {

    @Mock private PropertyRegistrationRepository propertyRepository;
    @Mock private MessRegistrationRepository messRepository;
    @Mock private OtpService otpService;
    @Mock private UserRepository userRepository;
    @Mock private SpaceService spaceService;
    @Mock private RegistrationPhotoService registrationPhotoService;
    @Mock private RegistrationOwnerLinker registrationOwnerLinker;

    private PropertyRegistrationService propertyService;
    private MessRegistrationService messService;

    @BeforeEach
    void setUp() {
        propertyService = new PropertyRegistrationService(
                propertyRepository,
                otpService,
                userRepository,
                spaceService,
                registrationPhotoService,
                registrationOwnerLinker);
        messService = new MessRegistrationService(
                messRepository,
                otpService,
                userRepository,
                spaceService,
                registrationPhotoService,
                registrationOwnerLinker);
    }

    @Test
    void importedMonthlyPgPriceUsesTheExistingPerBedBasis() {
        when(propertyRepository.nextReferenceNumber()).thenReturn(1L);
        when(propertyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        propertyService.registerImported(property("PG", "5500", null, false));

        PropertyRegistrationEntity saved = savedProperty();
        assertThat(saved.getStartingPrice()).isEqualByComparingTo("5500.00");
        assertThat(saved.getPriceBasis()).isEqualTo(PriceBasis.PER_BED);
    }

    @Test
    void importedNightlyPriceIsStoredWithoutAConvertedBasis() {
        when(propertyRepository.nextReferenceNumber()).thenReturn(1L);
        when(propertyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        propertyService.registerImported(property("PG", "5500", null, true));

        PropertyRegistrationEntity saved = savedProperty();
        assertThat(saved.getStartingPrice()).isEqualByComparingTo("5500.00");
        assertThat(saved.getPriceBasis()).isNull();
    }

    @Test
    void importedCorporateStayAmountStaysWithoutAGuessedBasis() {
        when(propertyRepository.nextReferenceNumber()).thenReturn(1L);
        when(propertyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        propertyService.registerImported(property("CORPORATE_STAY", "5500", null, false));

        PropertyRegistrationEntity saved = savedProperty();
        assertThat(saved.getStartingPrice()).isEqualByComparingTo("5500.00");
        assertThat(saved.getPriceBasis()).isNull();
    }

    @Test
    void importedPerRoomPriceKeepsThatBasis() {
        when(propertyRepository.nextReferenceNumber()).thenReturn(1L);
        when(propertyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        propertyService.registerImported(property("RENTAL", "5500", PriceBasis.PER_ROOM, false));

        PropertyRegistrationEntity saved = savedProperty();
        assertThat(saved.getStartingPrice()).isEqualByComparingTo("5500.00");
        assertThat(saved.getPriceBasis()).isEqualTo(PriceBasis.PER_ROOM);
    }

    @Test
    void importedMessMealUnitStaysAnAmountWithoutAMonthlyBasis() {
        when(messRepository.nextReferenceNumber()).thenReturn(1L);
        when(messRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AdminCreateMessRegistrationRequest request = new AdminCreateMessRegistrationRequest();
        request.setMessName("Good Mess");
        request.setMonthlyPrice(new BigDecimal("150"));
        request.setSuppressPriceBasisDefault(true);
        messService.registerImported(request);

        ArgumentCaptor<MessRegistrationEntity> captor = ArgumentCaptor.forClass(MessRegistrationEntity.class);
        org.mockito.Mockito.verify(messRepository).save(captor.capture());
        MessRegistrationEntity saved = captor.getValue();
        assertThat(saved.getMonthlyPrice()).isEqualByComparingTo("150.00");
        assertThat(saved.getMonthlyPriceBasis()).isNull();
        assertThat(saved.getMealPrice()).isEqualByComparingTo("0.00");
    }

    private static AdminCreatePropertyRegistrationRequest property(
            String type, String amount, PriceBasis basis, boolean suppressDefault) {
        AdminCreatePropertyRegistrationRequest request = new AdminCreatePropertyRegistrationRequest();
        request.setPropertyType(SpaceType.valueOf(type));
        request.setPropertyName("Sunrise PG");
        request.setStartingPrice(new BigDecimal(amount));
        request.setPriceBasis(basis);
        request.setSuppressPriceBasisDefault(suppressDefault);
        return request;
    }

    private PropertyRegistrationEntity savedProperty() {
        ArgumentCaptor<PropertyRegistrationEntity> captor =
                ArgumentCaptor.forClass(PropertyRegistrationEntity.class);
        org.mockito.Mockito.verify(propertyRepository).save(captor.capture());
        return captor.getValue();
    }
}
