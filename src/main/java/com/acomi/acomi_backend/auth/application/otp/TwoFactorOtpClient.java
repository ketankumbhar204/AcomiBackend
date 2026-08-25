package com.acomi.acomi_backend.auth.application.otp;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.config.security.OtpProperties;
import java.net.URI;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 2Factor.in AUTOGEN send + VERIFY3 verify. Provider HTTP stays isolated from auth business logic.
 * URIs contain the API key — they must never be logged or included in API responses.
 *
 * <p>Send: {@code /API/V1/{key}/SMS/{phone}/AUTOGEN/{template}}
 * <p>Verify: {@code /API/V1/{key}/SMS/VERIFY3/{phone}/{otp}}
 */
public class TwoFactorOtpClient {

    static final String SEND_UNAVAILABLE_MESSAGE = "Unable to send OTP. Please try again later.";
    static final String VERIFY_UNAVAILABLE_MESSAGE = "We couldn't verify the code right now. Please try again.";
    private static final String INVALID_OTP_MESSAGE = "Invalid OTP";
    private static final String EXPIRED_OTP_MESSAGE = "OTP has expired. Request a new one.";

    private static final Logger log = LoggerFactory.getLogger(TwoFactorOtpClient.class);

    private final OtpProperties.TwoFactor properties;
    private final RestClient restClient;

    public TwoFactorOtpClient(OtpProperties properties, RestClient restClient) {
        this.properties = properties.getTwoFactor();
        this.restClient = restClient;
    }

    static RestClient createRestClient(OtpProperties.TwoFactor twoFactor) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.max(1_000, twoFactor.getTimeoutMs())));
        factory.setReadTimeout(Duration.ofMillis(Math.max(1_000, twoFactor.getTimeoutMs())));
        return RestClient.builder().requestFactory(factory).build();
    }

    public void sendOtp(String tenDigitMobile) {
        String phone = toProviderPhone(tenDigitMobile);
        String template = StringUtils.hasText(properties.getTemplate()) ? properties.getTemplate().trim() : "OTP1";
        TwoFactorApiResponse response = execute(createSendUri(phone, template), "send");
        if (!response.isSuccess()) {
            throw mapSendError(response);
        }
        log.info("OTP dispatch requested via provider");
    }

    public void verifyOtp(String tenDigitMobile, String otp) {
        String phone = toProviderPhone(tenDigitMobile);
        TwoFactorApiResponse response = execute(createVerifyUri(phone, otp), "verify");
        if (!response.isSuccess()) {
            throw mapVerifyError(response);
        }
        log.info("OTP verified successfully via provider");
    }

    URI createSendUri(String phone, String template) {
        return providerUri("SMS", phone, "AUTOGEN", template);
    }

    URI createVerifyUri(String phone, String otp) {
        return providerUri("SMS", "VERIFY3", phone, otp);
    }

    String toProviderPhone(String tenDigitMobile) {
        String prefix = properties.getPhonePrefix() == null ? "" : properties.getPhonePrefix().replaceAll("\\D", "");
        return prefix + tenDigitMobile;
    }

    private URI providerUri(String... segments) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw unavailable("send");
        }
        String base = StringUtils.hasText(properties.getBaseUrl())
                ? properties.getBaseUrl().replaceAll("/+$", "")
                : "https://2factor.in/API/V1";
        return UriComponentsBuilder.fromUriString(base)
                .pathSegment(properties.getApiKey().trim())
                .pathSegment(segments)
                .build()
                .toUri();
    }

    private TwoFactorApiResponse execute(URI uri, String operation) {
        try {
            return restClient
                    .get()
                    .uri(uri)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((request, response) -> {
                        int httpStatus = response.getStatusCode().value();
                        TwoFactorApiResponse body;
                        try {
                            body = response.bodyTo(TwoFactorApiResponse.class);
                        } catch (RuntimeException ex) {
                            log.warn("OTP provider {} httpStatus={} response could not be read", operation, httpStatus);
                            throw unavailable(operation);
                        }
                        if (body == null || !StringUtils.hasText(body.status())) {
                            log.warn("OTP provider {} httpStatus={} returned an empty response", operation, httpStatus);
                            throw unavailable(operation);
                        }
                        if (body.isSuccess()) {
                            log.info("OTP provider {} httpStatus={} providerStatus=Success", operation, httpStatus);
                        } else {
                            log.warn(
                                    "OTP provider {} httpStatus={} providerStatus={} details={}",
                                    operation,
                                    httpStatus,
                                    sanitizeProviderText(body.status()),
                                    sanitizeProviderText(body.details()));
                        }
                        return body;
                    });
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.warn("OTP provider {} request failed", operation);
            throw unavailable(operation);
        } catch (RuntimeException ex) {
            log.warn("OTP provider {} response could not be read", operation);
            throw unavailable(operation);
        }
    }

    private BusinessException mapSendError(TwoFactorApiResponse response) {
        String details = response.detailsLower();
        if (details.contains("invalid")
                && (details.contains("number") || details.contains("mobile") || details.contains("phone"))) {
            return new BusinessException("Mobile number must be a 10-digit Indian number");
        }
        log.warn("OTP provider rejected send");
        return unavailable("send");
    }

    private BusinessException mapVerifyError(TwoFactorApiResponse response) {
        String details = response.detailsLower();
        if (details.contains("expired") || details.contains("session")) {
            return new BusinessException(EXPIRED_OTP_MESSAGE);
        }
        log.warn("OTP provider rejected verify");
        return new BusinessException(INVALID_OTP_MESSAGE);
    }

    private static BusinessException unavailable(String operation) {
        if ("verify".equals(operation)) {
            return new BusinessException(VERIFY_UNAVAILABLE_MESSAGE, HttpStatus.SERVICE_UNAVAILABLE);
        }
        return new BusinessException(SEND_UNAVAILABLE_MESSAGE, HttpStatus.SERVICE_UNAVAILABLE);
    }

    static String sanitizeProviderText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.trim()
                .replaceAll("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "[id]")
                .replaceAll("\\d{4,}", "[n]");
    }
}
