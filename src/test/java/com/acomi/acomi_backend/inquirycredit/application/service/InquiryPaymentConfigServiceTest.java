package com.acomi.acomi_backend.inquirycredit.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.inquirycredit.api.dto.request.UpdatePaymentConfigRequest;
import com.acomi.acomi_backend.inquirycredit.api.dto.response.InquiryPaymentConfigResponse;
import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.entity.InquiryPaymentConfigEntity;
import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.repository.InquiryCreditPackageRepository;
import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.repository.InquiryPaymentConfigRepository;
import com.acomi.acomi_backend.storage.application.service.StoredFileService;
import com.acomi.acomi_backend.storage.domain.model.FilePurpose;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InquiryPaymentConfigServiceTest {

    @Mock
    private InquiryPaymentConfigRepository configRepository;

    @Mock
    private InquiryCreditPackageRepository packageRepository;

    @Mock
    private StoredFileService storedFileService;

    private InquiryPaymentConfigService service;
    private InquiryPaymentConfigEntity config;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        service = new InquiryPaymentConfigService(configRepository, packageRepository, storedFileService);
        adminId = UUID.randomUUID();
        config = InquiryPaymentConfigEntity.builder()
                .enabled(true)
                .upiId("shop@upi")
                .qrFileId(UUID.randomUUID())
                .whatsappNumber("919800000000")
                .instructions("Pay then send the screenshot.")
                .webFreeDailyLimit(4)
                .viewHereDailyLimit(7)
                .webViewHereEnabled(true)
                .androidViewHereEnabled(true)
                .androidViewHereDailyLimit(3)
                .androidBillingMode("FREE")
                .androidFreeDailyLimit(6)
                .androidHourlyRateLimit(20)
                .build();
        config.setId(UUID.randomUUID());
        when(configRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(config));
        lenient().when(configRepository.save(config)).thenReturn(config);
        lenient().when(packageRepository.findAll()).thenReturn(List.of());
        lenient().when(storedFileService.tryRetainInquiryPaymentQr(any())).thenReturn(true);
        lenient()
                .when(storedFileService.resolveContentUrlIsolated(any(), any()))
                .thenReturn("https://files.example/qr.png");
    }

    @Test
    void adminUpdate_persistsContactViewSettingsSeparatelyFromEnquiryQuotas() {
        UpdatePaymentConfigRequest request = new UpdatePaymentConfigRequest();
        request.setWebFreeDailyLimit(2);
        request.setViewHereDailyLimit(0);
        request.setWebViewHereEnabled(false);
        request.setAndroidViewHereEnabled(true);
        request.setAndroidViewHereDailyLimit(9);
        request.setAndroidBillingMode("CREDITS");
        request.setAndroidFreeDailyLimit(8);
        request.setAndroidHourlyRateLimit(15);

        InquiryPaymentConfigResponse response = service.adminUpdate(request, adminId);

        assertThat(config.getWebFreeDailyLimit()).isEqualTo(2);
        assertThat(config.getViewHereDailyLimit()).isZero();
        assertThat(config.isWebViewHereEnabled()).isFalse();
        assertThat(config.isAndroidViewHereEnabled()).isTrue();
        assertThat(config.getAndroidViewHereDailyLimit()).isEqualTo(9);
        assertThat(config.getAndroidFreeDailyLimit()).isEqualTo(8);
        assertThat(config.getAndroidBillingMode()).isEqualTo("CREDITS");
        assertThat(config.getAndroidHourlyRateLimit()).isEqualTo(15);
        assertThat(config.getUpiId()).isEqualTo("shop@upi");
        assertThat(config.getQrFileId()).isNotNull();
        assertThat(response.getViewHereDailyLimit()).isZero();
        assertThat(response.isWebViewHereEnabled()).isFalse();
        assertThat(response.getAndroidViewHereDailyLimit()).isEqualTo(9);
        assertThat(response.isAndroidViewHereEnabled()).isTrue();
        assertThat(response.getWebFreeDailyLimit()).isEqualTo(2);
        assertThat(response.getConfigId()).isEqualTo(config.getId());
    }

    @Test
    void adminUpdate_keepsStoredLimitsWhenContactViewingIsDisabled() {
        UpdatePaymentConfigRequest request = new UpdatePaymentConfigRequest();
        request.setWebViewHereEnabled(false);
        request.setAndroidViewHereEnabled(false);

        service.adminUpdate(request, adminId);

        assertThat(config.isWebViewHereEnabled()).isFalse();
        assertThat(config.isAndroidViewHereEnabled()).isFalse();
        assertThat(config.getViewHereDailyLimit()).isEqualTo(7);
        assertThat(config.getAndroidViewHereDailyLimit()).isEqualTo(3);
        assertThat(config.getWebFreeDailyLimit()).isEqualTo(4);
        assertThat(config.getAndroidFreeDailyLimit()).isEqualTo(6);
    }

    @Test
    void adminUpdate_doesNotClearPaymentDetailsWhenThoseFieldsAreOmitted() {
        UUID qrFileId = config.getQrFileId();
        UpdatePaymentConfigRequest request = new UpdatePaymentConfigRequest();
        request.setWebFreeDailyLimit(1);

        service.adminUpdate(request, adminId);

        assertThat(config.getQrFileId()).isEqualTo(qrFileId);
        assertThat(config.getUpiId()).isEqualTo("shop@upi");
        assertThat(config.getWhatsappNumber()).isEqualTo("919800000000");
        assertThat(config.getInstructions()).isEqualTo("Pay then send the screenshot.");
        assertThat(config.getViewHereDailyLimit()).isEqualTo(7);
    }

    @Test
    void adminUpdate_rejectsNegativeContactViewLimitsWithoutChangingTheRow() {
        UpdatePaymentConfigRequest request = new UpdatePaymentConfigRequest();
        request.setViewHereDailyLimit(-1);

        assertThatThrownBy(() -> service.adminUpdate(request, adminId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Website contact-view limit");
        assertThat(config.getViewHereDailyLimit()).isEqualTo(7);
    }

    @Test
    void adminGet_returnsPaymentDetailsWhenQrResolutionFails() {
        when(storedFileService.tryRetainInquiryPaymentQr(config.getQrFileId())).thenReturn(true);
        when(storedFileService.resolveContentUrlIsolated(adminId, config.getQrFileId()))
                .thenThrow(new RuntimeException("qr missing"));

        InquiryPaymentConfigResponse response = service.adminGet(adminId);

        assertThat(response.getUpiId()).isEqualTo("shop@upi");
        assertThat(response.getWhatsappNumber()).isEqualTo("919800000000");
        assertThat(response.getInstructions()).isEqualTo("Pay then send the screenshot.");
        assertThat(response.isEnabled()).isTrue();
        assertThat(response.getQrFileId()).isEqualTo(config.getQrFileId());
        assertThat(response.getQrUrl()).isNull();
    }

    @Test
    void adminGet_returnsPaymentDetailsWhenQrFileIsGone() {
        when(storedFileService.tryRetainInquiryPaymentQr(config.getQrFileId())).thenReturn(false);

        InquiryPaymentConfigResponse response = service.adminGet(adminId);

        assertThat(response.getUpiId()).isEqualTo("shop@upi");
        assertThat(response.getWhatsappNumber()).isEqualTo("919800000000");
        assertThat(response.getQrFileId()).isNull();
        assertThat(config.getQrFileId()).isNotNull();
    }

    @Test
    void adminUpdate_replacesQrWithoutClearingOtherPaymentDetails() {
        UUID previous = config.getQrFileId();
        UUID next = UUID.randomUUID();
        UpdatePaymentConfigRequest request = new UpdatePaymentConfigRequest();
        request.setQrFileId(next);
        when(storedFileService.requireActiveForAssociation(
                        eq(adminId), eq(next), eq(FilePurpose.INQUIRY_PAYMENT_QR), isNull()))
                .thenReturn(null);

        service.adminUpdate(request, adminId);

        assertThat(config.getQrFileId()).isEqualTo(next);
        assertThat(config.getUpiId()).isEqualTo("shop@upi");
        verify(storedFileService).replaceAssociation(previous, next);
    }

    @Test
    void adminUpdate_clearQrRemovesOnlyTheImage() {
        UUID previous = config.getQrFileId();
        UpdatePaymentConfigRequest request = new UpdatePaymentConfigRequest();
        request.setClearQr(true);

        service.adminUpdate(request, adminId);

        assertThat(config.getQrFileId()).isNull();
        assertThat(config.getUpiId()).isEqualTo("shop@upi");
        assertThat(config.getWhatsappNumber()).isEqualTo("919800000000");
        verify(storedFileService).scheduleDelete(previous);
    }
}
