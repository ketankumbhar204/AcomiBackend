package com.acomi.acomi_backend.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.admin.api.dto.request.AdminSendListingMarketingWhatsAppRequest;
import com.acomi.acomi_backend.admin.api.dto.response.AdminListingMarketingResponse;
import com.acomi.acomi_backend.admin.domain.model.ListingMarketingOutcome;
import com.acomi.acomi_backend.admin.domain.model.ListingMarketingStatus;
import com.acomi.acomi_backend.admin.domain.model.WhatsAppAvailability;
import com.acomi.acomi_backend.admin.infrastructure.config.MarketingWhatsAppProperties;
import com.acomi.acomi_backend.admin.infrastructure.persistence.entity.AdminListingMarketingActivityEntity;
import com.acomi.acomi_backend.admin.infrastructure.persistence.repository.AdminListingMarketingActivityRepository;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import com.acomi.acomi_backend.common.security.SecurityUtils;

@ExtendWith(MockitoExtension.class)
class AdminListingMarketingWhatsAppServiceTest {

    @Mock
    private AdminListingMarketingService marketingService;

    @Mock
    private AdminListingMarketingActivityRepository activityRepository;

    @Mock
    private PropertyRegistrationRepository propertyRegistrationRepository;

    @Mock
    private MessRegistrationRepository messRegistrationRepository;

    @Mock
    private SpaceRepository spaceRepository;

    private MarketingWhatsAppProperties properties;
    private AdminListingMarketingWhatsAppService service;

    @BeforeEach
    void setUp() {
        properties = new MarketingWhatsAppProperties();
        service = new AdminListingMarketingWhatsAppService(
                properties,
                marketingService,
                activityRepository,
                propertyRegistrationRepository,
                messRegistrationRepository,
                spaceRepository);
    }

    @Test
    void capability_unavailableByDefault() {
        var capability = service.capability();
        assertThat(capability.isAvailable()).isFalse();
        assertThat(capability.getReasons()).contains("INTEGRATION_DISABLED", "SENDER_NOT_READY");
        assertThat(capability.getExplanation()).contains("Manual");
    }

    @Test
    void sendForProperty_blocksWhenNotConfigured_andRecordsActivity() {
        UUID registrationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(propertyRegistrationRepository.existsById(registrationId)).thenReturn(true);
        when(propertyRegistrationRepository.findById(registrationId)).thenReturn(java.util.Optional.empty());
        when(activityRepository.save(any())).thenAnswer(inv -> {
            AdminListingMarketingActivityEntity entity = inv.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });
        when(marketingService.getPropertyRegistrationMarketing(registrationId))
                .thenReturn(AdminListingMarketingResponse.builder()
                        .marketingStatus(ListingMarketingStatus.NOT_SHARED)
                        .whatsappAvailability(WhatsAppAvailability.UNKNOWN)
                        .activities(List.of())
                        .build());

        AdminSendListingMarketingWhatsAppRequest request = new AdminSendListingMarketingWhatsAppRequest();
        request.setRecipientPhone("9876543210");
        request.setMessageSnapshot("Hello");
        request.setMediaFileNames(List.of("promo.jpg"));

        try (MockedStatic<SecurityUtils> security = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(actorId);
            var result = service.sendForProperty(registrationId, request);
            assertThat(result.isAccepted()).isFalse();
            assertThat(result.getOutcome()).isEqualTo(ListingMarketingOutcome.WHATSAPP_API_BLOCKED);
            assertThat(result.getExplanation()).isNotBlank();
        }

        ArgumentCaptor<AdminListingMarketingActivityEntity> captor =
                ArgumentCaptor.forClass(AdminListingMarketingActivityEntity.class);
        verify(activityRepository).save(captor.capture());
        assertThat(captor.getValue().getOutcome()).isEqualTo(ListingMarketingOutcome.WHATSAPP_API_BLOCKED);
        assertThat(captor.getValue().getRecipientPhoneMasked()).endsWith("3210");
        assertThat(captor.getValue().getNote()).contains("promo.jpg");
    }

    @Test
    void sendForProperty_rejectsDuplicateWithinWindow() {
        UUID registrationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(propertyRegistrationRepository.existsById(registrationId)).thenReturn(true);
        when(propertyRegistrationRepository.findById(registrationId)).thenReturn(java.util.Optional.empty());
        when(activityRepository.save(any())).thenAnswer(inv -> {
            AdminListingMarketingActivityEntity entity = inv.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });
        when(marketingService.getPropertyRegistrationMarketing(registrationId))
                .thenReturn(AdminListingMarketingResponse.builder()
                        .marketingStatus(ListingMarketingStatus.NOT_SHARED)
                        .whatsappAvailability(WhatsAppAvailability.UNKNOWN)
                        .activities(List.of())
                        .build());

        AdminSendListingMarketingWhatsAppRequest request = new AdminSendListingMarketingWhatsAppRequest();
        request.setRecipientPhone("9876543210");
        request.setClientRequestId("tap-1");

        try (MockedStatic<SecurityUtils> security = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(actorId);
            service.sendForProperty(registrationId, request);
            assertThatThrownBy(() -> service.sendForProperty(registrationId, request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("already submitted");
        }
    }
}
