package com.acomi.acomi_backend.payment.infrastructure.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class MetaCloudWhatsAppClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void buildRequestBody_includesTemplateParameters() {
        WhatsAppReminderProperties props = new WhatsAppReminderProperties();
        props.setPhoneNumberId("123");
        props.setAccessToken("secret-token-value");
        MetaCloudWhatsAppClient client = new MetaCloudWhatsAppClient(props, mapper);

        var body = client.buildRequestBody(WhatsAppTemplateSendRequest.builder()
                .toDigits("919876543210")
                .templateName("acomi_payment_overdue")
                .languageCode("en")
                .bodyParameters(List.of("Rahul", "rent", "ABC PG", "₹10000.00", "01 Sep 2026", "3"))
                .build());

        assertThat(body.get("to").asText()).isEqualTo("919876543210");
        assertThat(body.path("template").path("name").asText()).isEqualTo("acomi_payment_overdue");
        assertThat(body.path("template").path("components").get(0).path("parameters")).hasSize(6);
        assertThat(client.buildMessagesUrl()).endsWith("/123/messages");
    }

    @Test
    void mapHttpResponse_successExtractsMessageId() {
        WhatsAppReminderProperties props = new WhatsAppReminderProperties();
        MetaCloudWhatsAppClient client = new MetaCloudWhatsAppClient(props, mapper);
        var result = client.mapHttpResponse(
                200, "{\"messages\":[{\"id\":\"wamid.TEST123\"}]}");
        assertThat(result.success()).isTrue();
        assertThat(result.providerMessageId()).isEqualTo("wamid.TEST123");
    }

    @Test
    void mapHttpResponse_401_isAuthFailedNotRetryable() {
        WhatsAppReminderProperties props = new WhatsAppReminderProperties();
        MetaCloudWhatsAppClient client = new MetaCloudWhatsAppClient(props, mapper);
        var result = client.mapHttpResponse(401, "{\"error\":{\"message\":\"Invalid OAuth access token\"}}");
        assertThat(result.success()).isFalse();
        assertThat(result.failureCode()).isEqualTo("PROVIDER_AUTH_FAILED");
        assertThat(result.retryable()).isFalse();
    }

    @Test
    void mapHttpResponse_429_isRateLimitedRetryable() {
        WhatsAppReminderProperties props = new WhatsAppReminderProperties();
        MetaCloudWhatsAppClient client = new MetaCloudWhatsAppClient(props, mapper);
        var result = client.mapHttpResponse(429, "{\"error\":{\"message\":\"Rate limit hit\"}}");
        assertThat(result.failureCode()).isEqualTo("PROVIDER_RATE_LIMITED");
        assertThat(result.retryable()).isTrue();
    }

    @Test
    void sanitize_redactsPhonesAndTokens() {
        String raw = MetaCloudWhatsAppClient.sanitize(
                "Bearer secret.token.here failed for +919876543210 access_token");
        assertThat(raw).doesNotContain("919876543210");
        assertThat(raw).contains("[phone]");
        assertThat(raw).contains("Bearer [redacted]");
    }
}
