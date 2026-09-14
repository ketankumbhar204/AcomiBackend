package com.acomi.acomi_backend.payment.infrastructure.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.payment.application.dto.PaymentReminderMessage;
import com.acomi.acomi_backend.payment.domain.model.PaymentReminderType;
import com.acomi.acomi_backend.payment.domain.model.ReminderChannel;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WhatsAppMessageDeliveryProviderTest {

    private WhatsAppReminderProperties properties;
    private WhatsAppVendorClient vendorClient;
    private WhatsAppMessageDeliveryProvider provider;

    @BeforeEach
    void setUp() {
        properties = new WhatsAppReminderProperties();
        properties.setDefaultCountryCode("91");
        properties.setPaymentOverdueTemplate("acomi_payment_overdue");
        properties.setTemplateLanguage("en");
        vendorClient = mock(WhatsAppVendorClient.class);
        provider = new WhatsAppMessageDeliveryProvider(
                properties,
                new WhatsAppPhoneNormalizer(properties),
                new PaymentOverdueWhatsAppTemplateBuilder(properties),
                vendorClient);
    }

    @Test
    void disabledMode_isUnavailable() {
        properties.setMode("disabled");
        assertThat(provider.isConfigured()).isFalse();
        var result = provider.deliver(sample("9876543210"));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isProviderUnavailable()).isTrue();
        assertThat(result.getFailureCode()).isEqualTo("WHATSAPP_PROVIDER_NOT_CONFIGURED");
        verify(vendorClient, never()).sendTemplate(any());
    }

    @Test
    void loggingMode_succeedsWithoutVendor() {
        properties.setMode("logging");
        assertThat(provider.isConfigured()).isTrue();
        var result = provider.deliver(sample("9876543210"));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getProviderMessageId()).startsWith("log-wa-");
        verify(vendorClient, never()).sendTemplate(any());
    }

    @Test
    void loggingMode_rejectsInvalidPhone() {
        properties.setMode("logging");
        var result = provider.deliver(sample("12"));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFailureCode()).isEqualTo("INVALID_RECIPIENT");
        assertThat(result.isRetryable()).isFalse();
    }

    @Test
    void liveMode_withoutCredentials_notConfigured() {
        properties.setMode("live");
        assertThat(provider.isConfigured()).isFalse();
        var result = provider.deliver(sample("9876543210"));
        assertThat(result.getFailureCode()).isEqualTo("WHATSAPP_PROVIDER_NOT_CONFIGURED");
        verify(vendorClient, never()).sendTemplate(any());
    }

    @Test
    void liveMode_successfulVendorResponse() {
        properties.setMode("live");
        properties.setAccessToken("test-token");
        properties.setPhoneNumberId("phone-id");
        when(vendorClient.sendTemplate(any()))
                .thenReturn(WhatsAppVendorClient.VendorSendResult.ok("wamid.ABC"));

        var result = provider.deliver(sample("9876543210"));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getProviderMessageId()).isEqualTo("wamid.ABC");
        verify(vendorClient).sendTemplate(any());
    }

    @Test
    void liveMode_mapsVendorFailure() {
        properties.setMode("live");
        properties.setAccessToken("test-token");
        properties.setPhoneNumberId("phone-id");
        when(vendorClient.sendTemplate(any()))
                .thenReturn(WhatsAppVendorClient.VendorSendResult.fail(
                        "PROVIDER_TIMEOUT", "timeout", true));

        var result = provider.deliver(sample("9876543210"));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFailureCode()).isEqualTo("PROVIDER_TIMEOUT");
        assertThat(result.isRetryable()).isTrue();
    }

    private static PaymentReminderMessage sample(String mobile) {
        return PaymentReminderMessage.builder()
                .paymentId(UUID.randomUUID())
                .spaceId(UUID.randomUUID())
                .memberId(UUID.randomUUID())
                .memberName("Rahul")
                .spaceName("ABC PG")
                .recipientMobile(mobile)
                .reminderType(PaymentReminderType.RENT_PAYMENT_OVERDUE)
                .channel(ReminderChannel.WHATSAPP)
                .paymentType(SpacePaymentType.RENT)
                .outstandingAmount(new BigDecimal("10000"))
                .currencyCode("INR")
                .daysOverdue(3)
                .dueDate(LocalDate.of(2026, 9, 1))
                .businessDate(LocalDate.of(2026, 9, 4))
                .build();
    }
}
