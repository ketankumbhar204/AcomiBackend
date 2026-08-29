package com.acomi.acomi_backend.auth.application.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.config.security.OtpProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TwoFactorOtpClientTest {

    private static final String TEST_KEY = "unit-test-twofactor-key";
    private static final String MOBILE = "9876543210";
    private static final String OTP = "131072";

    private OtpProperties properties;
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer server;
    private TwoFactorOtpClient client;

    @BeforeEach
    void setUp() {
        properties = new OtpProperties();
        properties.getTwoFactor().setApiKey(TEST_KEY);
        properties.getTwoFactor().setBaseUrl("https://2factor.in/API/V1");
        properties.getTwoFactor().setTemplate("OTP1");
        restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        client = new TwoFactorOtpClient(properties, restClientBuilder.build());
    }

    @Test
    void createVerifyUri_usesVerify3BeforePhone() {
        assertThat(client.createVerifyUri(MOBILE, OTP).toString())
                .isEqualTo("https://2factor.in/API/V1/" + TEST_KEY + "/SMS/VERIFY3/" + MOBILE + "/" + OTP);
        assertThat(client.createSendUri(MOBILE, "OTP1").toString())
                .isEqualTo("https://2factor.in/API/V1/" + TEST_KEY + "/SMS/" + MOBILE + "/AUTOGEN/OTP1");
    }

    @Test
    void sendOtp_successDoesNotLogSecretOrOtp() {
        server.expect(requestTo(
                        "https://2factor.in/API/V1/" + TEST_KEY + "/SMS/" + MOBILE + "/AUTOGEN/OTP1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"Status\":\"Success\",\"Details\":\"session-id\"}", MediaType.APPLICATION_JSON));

        Logger logger = (Logger) LoggerFactory.getLogger(TwoFactorOtpClient.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        client.sendOtp(MOBILE);

        server.verify();
        String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + b);
        assertThat(logs).doesNotContain(TEST_KEY);
        assertThat(logs).doesNotContain("session-id");
        assertThat(logs).doesNotContain("/API/V1/");
        logger.detachAppender(appender);
    }

    @Test
    void providerErrorDetailsRedactApiKeyAndAreNotReturnedToClient() {
        server.expect(requestTo(
                        "https://2factor.in/API/V1/" + TEST_KEY + "/SMS/" + MOBILE + "/AUTOGEN/OTP1"))
                .andRespond(withSuccess(
                        "{\"Status\":\"Error\",\"Details\":\"unauthorized " + TEST_KEY + "\"}",
                        MediaType.APPLICATION_JSON));

        Logger logger = (Logger) LoggerFactory.getLogger(TwoFactorOtpClient.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        assertThatThrownBy(() -> client.sendOtp(MOBILE))
                .isInstanceOf(BusinessException.class)
                .hasMessage(TwoFactorOtpClient.SEND_UNAVAILABLE_MESSAGE)
                .extracting(ex -> ((BusinessException) ex).getMessage())
                .isNotEqualTo(TEST_KEY);

        String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + b);
        assertThat(logs).doesNotContain(TEST_KEY);
        assertThat(logs).contains("[redacted]");
        logger.detachAppender(appender);
        server.verify();
    }

    @Test
    void verifyOtp_success() {
        server.expect(requestTo(
                        "https://2factor.in/API/V1/" + TEST_KEY + "/SMS/VERIFY3/" + MOBILE + "/" + OTP))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"Status\":\"Success\",\"Details\":\"OTP Matched\"}", MediaType.APPLICATION_JSON));

        client.verifyOtp(MOBILE, OTP);
        server.verify();
    }

    @Test
    void verifyOtp_acceptsLowercaseJsonKeys() {
        server.expect(requestTo(
                        "https://2factor.in/API/V1/" + TEST_KEY + "/SMS/VERIFY3/" + MOBILE + "/" + OTP))
                .andRespond(withSuccess("{\"status\":\"Success\",\"details\":\"OTP Matched\"}", MediaType.APPLICATION_JSON));

        client.verifyOtp(MOBILE, OTP);
        server.verify();
    }

    @Test
    void verifyOtp_invalidIsMappedWithoutProviderDetails() {
        server.expect(requestTo(
                        "https://2factor.in/API/V1/" + TEST_KEY + "/SMS/VERIFY3/" + MOBILE + "/111111"))
                .andRespond(withSuccess("{\"Status\":\"Error\",\"Details\":\"OTP Mismatch\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.verifyOtp(MOBILE, "111111"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid OTP")
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        server.verify();
    }

    @Test
    void verifyOtp_httpErrorBodyIsMappedToInvalidOtpNotSendUnavailable() {
        server.expect(requestTo(
                        "https://2factor.in/API/V1/" + TEST_KEY + "/SMS/VERIFY3/" + MOBILE + "/111111"))
                .andRespond(withBadRequest()
                        .body("{\"Status\":\"Error\",\"Details\":\"OTP Mismatch\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.verifyOtp(MOBILE, "111111"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid OTP");
        server.verify();
    }

    @Test
    void verifyOtp_expiredIsMapped() {
        server.expect(requestTo(
                        "https://2factor.in/API/V1/" + TEST_KEY + "/SMS/VERIFY3/" + MOBILE + "/482731"))
                .andRespond(withSuccess("{\"Status\":\"Error\",\"Details\":\"OTP Expired\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.verifyOtp(MOBILE, "482731"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("OTP has expired. Request a new one.");
        server.verify();
    }

    @Test
    void verifyOtp_providerHttpFailureIsVerifyUnavailable() {
        server.expect(requestTo(
                        "https://2factor.in/API/V1/" + TEST_KEY + "/SMS/VERIFY3/" + MOBILE + "/" + OTP))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.verifyOtp(MOBILE, OTP))
                .isInstanceOf(BusinessException.class)
                .hasMessage(TwoFactorOtpClient.VERIFY_UNAVAILABLE_MESSAGE)
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        server.verify();
    }

    @Test
    void verifyOtp_malformedResponseIsVerifyUnavailable() {
        server.expect(requestTo(
                        "https://2factor.in/API/V1/" + TEST_KEY + "/SMS/VERIFY3/" + MOBILE + "/" + OTP))
                .andRespond(withSuccess("<html>not-json</html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> client.verifyOtp(MOBILE, OTP))
                .isInstanceOf(BusinessException.class)
                .hasMessage(TwoFactorOtpClient.VERIFY_UNAVAILABLE_MESSAGE);
        server.verify();
    }

    @Test
    void sendOtp_providerFailureIsUnavailable() {
        server.expect(requestTo(
                        "https://2factor.in/API/V1/" + TEST_KEY + "/SMS/" + MOBILE + "/AUTOGEN/OTP1"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.sendOtp(MOBILE))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Unable to send OTP. Please try again later.")
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        server.verify();
    }

    @Test
    void toProviderPhone_usesTenDigitNationalNumberByDefault() {
        assertThat(client.toProviderPhone(MOBILE)).isEqualTo(MOBILE);
        properties.getTwoFactor().setPhonePrefix("91");
        assertThat(client.toProviderPhone(MOBILE)).isEqualTo("91" + MOBILE);
    }

    @Test
    void sanitizeProviderText_stripsIdsAndDigitRuns() {
        assertThat(TwoFactorOtpClient.sanitizeProviderText("OTP Mismatch")).isEqualTo("OTP Mismatch");
        assertThat(TwoFactorOtpClient.sanitizeProviderText("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee 131072"))
                .isEqualTo("[id] [n]");
    }
}
