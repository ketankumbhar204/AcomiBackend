package com.acomi.acomi_backend.payment.infrastructure.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Meta WhatsApp Cloud API client.
 *
 * <p>POST {@code /{apiVersion}/{phone-number-id}/messages} with a template payload.
 * Access token is never logged.
 */
@Component
@Slf4j
public class MetaCloudWhatsAppClient implements WhatsAppVendorClient {

    private final WhatsAppReminderProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Autowired
    public MetaCloudWhatsAppClient(WhatsAppReminderProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = createRestClient(properties);
    }

    /** Test/construction hook. */
    MetaCloudWhatsAppClient(
            WhatsAppReminderProperties properties, ObjectMapper objectMapper, RestClient restClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    static RestClient createRestClient(WhatsAppReminderProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.max(1_000, properties.getConnectTimeoutMs())));
        factory.setReadTimeout(Duration.ofMillis(Math.max(1_000, properties.getReadTimeoutMs())));
        return RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public VendorSendResult sendTemplate(WhatsAppTemplateSendRequest request) {
        if (!StringUtils.hasText(properties.getAccessToken())
                || !StringUtils.hasText(properties.getPhoneNumberId())) {
            return VendorSendResult.fail(
                    "PROVIDER_NOT_CONFIGURED", "WhatsApp live credentials are incomplete", false);
        }
        if (!StringUtils.hasText(request.getTemplateName())) {
            return VendorSendResult.fail("TEMPLATE_ERROR", "Payment overdue template name is not configured", false);
        }

        String url = buildMessagesUrl();
        ObjectNode body = buildRequestBody(request);

        try {
            return restClient
                    .post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getAccessToken().trim())
                    .body(body)
                    .exchange((req, response) -> {
                        int status = response.getStatusCode().value();
                        String raw;
                        try {
                            raw = response.bodyTo(String.class);
                        } catch (RuntimeException ex) {
                            log.warn("WhatsApp provider response body unreadable httpStatus={}", status);
                            return VendorSendResult.fail("PROVIDER_ERROR", "Unreadable provider response", true);
                        }
                        return mapHttpResponse(status, raw);
                    });
        } catch (RestClientException ex) {
            // Do not log exception message — may contain request URI fragments.
            log.warn("WhatsApp provider request failed (timeout/network)");
            return VendorSendResult.fail("PROVIDER_TIMEOUT", "Provider timeout or network failure", true);
        } catch (RuntimeException ex) {
            log.warn("WhatsApp provider unexpected failure type={}", ex.getClass().getSimpleName());
            return VendorSendResult.fail("PROVIDER_ERROR", "Unexpected provider failure", true);
        }
    }

    String buildMessagesUrl() {
        String base = properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()
                ? "https://graph.facebook.com"
                : properties.getBaseUrl().replaceAll("/+$", "");
        String version = properties.getApiVersion() == null || properties.getApiVersion().isBlank()
                ? "v21.0"
                : properties.getApiVersion().trim();
        if (!version.startsWith("v")) {
            version = "v" + version;
        }
        return base + "/" + version + "/" + properties.getPhoneNumberId().trim() + "/messages";
    }

    ObjectNode buildRequestBody(WhatsAppTemplateSendRequest request) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("messaging_product", "whatsapp");
        root.put("to", request.getToDigits());
        root.put("type", "template");

        ObjectNode template = root.putObject("template");
        template.put("name", request.getTemplateName());
        ObjectNode language = template.putObject("language");
        language.put("code", request.getLanguageCode());

        List<String> params = request.getBodyParameters() == null ? List.of() : request.getBodyParameters();
        if (!params.isEmpty()) {
            ArrayNode components = template.putArray("components");
            ObjectNode bodyComponent = components.addObject();
            bodyComponent.put("type", "body");
            ArrayNode parameters = bodyComponent.putArray("parameters");
            for (String value : params) {
                ObjectNode param = parameters.addObject();
                param.put("type", "text");
                param.put("text", value == null ? "" : value);
            }
        }
        return root;
    }

    VendorSendResult mapHttpResponse(int httpStatus, String rawBody) {
        if (httpStatus >= 200 && httpStatus < 300) {
            String messageId = extractMessageId(rawBody);
            if (!StringUtils.hasText(messageId)) {
                log.warn("WhatsApp provider success without message id httpStatus={}", httpStatus);
                return VendorSendResult.fail("PROVIDER_ERROR", "Success response missing message id", true);
            }
            log.info("WhatsApp provider accepted message httpStatus={} providerMessageId={}", httpStatus, messageId);
            return VendorSendResult.ok(messageId);
        }

        String code = mapFailureCode(httpStatus, rawBody);
        boolean retryable = isRetryable(httpStatus, code);
        log.warn(
                "WhatsApp provider rejected send httpStatus={} failureCode={} details={}",
                httpStatus,
                code,
                sanitize(rawBody));
        return VendorSendResult.fail(code, truncate(sanitize(rawBody), 400), retryable);
    }

    private String extractMessageId(String rawBody) {
        if (!StringUtils.hasText(rawBody)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode messages = root.path("messages");
            if (messages.isArray() && !messages.isEmpty()) {
                return textOrNull(messages.get(0).path("id"));
            }
            return textOrNull(root.path("id"));
        } catch (Exception ex) {
            return null;
        }
    }

    private static String mapFailureCode(int httpStatus, String rawBody) {
        String lower = rawBody == null ? "" : rawBody.toLowerCase();
        if (httpStatus == 401 || httpStatus == 403 || lower.contains("oauth") || lower.contains("access token")) {
            return "PROVIDER_AUTH_FAILED";
        }
        if (httpStatus == 429 || lower.contains("rate limit") || lower.contains("too many")) {
            return "PROVIDER_RATE_LIMITED";
        }
        if (httpStatus == 400
                && (lower.contains("template") || lower.contains("parameter") || lower.contains("#132000"))) {
            return "TEMPLATE_ERROR";
        }
        if (httpStatus == 400
                && (lower.contains("phone") || lower.contains("recipient") || lower.contains("not a valid"))) {
            return "INVALID_RECIPIENT";
        }
        if (httpStatus >= 500) {
            return "PROVIDER_ERROR";
        }
        return "PROVIDER_ERROR";
    }

    private static boolean isRetryable(int httpStatus, String code) {
        if ("PROVIDER_RATE_LIMITED".equals(code) || "PROVIDER_TIMEOUT".equals(code)) {
            return true;
        }
        if ("PROVIDER_AUTH_FAILED".equals(code)
                || "TEMPLATE_ERROR".equals(code)
                || "INVALID_RECIPIENT".equals(code)
                || "PROVIDER_NOT_CONFIGURED".equals(code)) {
            return false;
        }
        return httpStatus >= 500 || httpStatus == 408 || httpStatus == 429;
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String text = node.asText();
        return StringUtils.hasText(text) ? text : null;
    }

    static String sanitize(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.trim()
                .replaceAll("(?i)\"access_token\"\\s*:\\s*\"[^\"]+\"", "\"access_token\":\"[redacted]\"")
                .replaceAll("Bearer\\s+[A-Za-z0-9._\\-]+", "Bearer [redacted]")
                .replaceAll("\\+?\\d{10,15}", "[phone]")
                .replaceAll("\\s+", " ");
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
