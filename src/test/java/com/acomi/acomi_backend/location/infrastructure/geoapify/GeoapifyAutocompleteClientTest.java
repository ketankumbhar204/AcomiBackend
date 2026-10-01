package com.acomi.acomi_backend.location.infrastructure.geoapify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.acomi.acomi_backend.location.config.GeoapifyProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import java.net.SocketTimeoutException;
import org.springframework.web.client.RestClient;

class GeoapifyAutocompleteClientTest {

    private static final String TEST_KEY = "unit-test-geoapify-key";

    private GeoapifyProperties properties;
    private MockRestServiceServer server;
    private GeoapifyAutocompleteClient client;

    @BeforeEach
    void setUp() {
        properties = new GeoapifyProperties();
        properties.setApiKey(TEST_KEY);
        properties.setBaseUrl("https://api.geoapify.com");
        properties.setLimit(8);
        properties.setCountryCode("in");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GeoapifyAutocompleteClient(properties, builder.build(), new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @Test
    void requestUriFiltersIndiaAndLimitsResults() {
        String uri = client.requestUri("Hinjewadi").toString();
        assertThat(uri).contains("countrycode");
        assertThat(uri).contains("limit=20");
        assertThat(uri).contains("type=locality");
        assertThat(client.requestUri("Aundh Pune").toString()).doesNotContain("type=");
        assertThat(client.requestUri("411057").toString()).contains("type=postcode");
        assertThat(uri).doesNotContain("countrycode:us");
        assertThat(uri).doesNotContain("countrycode%3Aus");
    }

    @Test
    void successMapsResultsAndDoesNotLogTheKey() {
        server.expect(requestTo(containsString("text=Hinjewadi")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(sampleBody(), MediaType.APPLICATION_JSON));
        ListAppender<ILoggingEvent> appender = attachLogs();

        GeoapifyAutocompleteClient.ProviderResult result = client.autocomplete("Hinjewadi");

        server.verify();
        assertThat(result.available()).isTrue();
        assertThat(result.places()).hasSize(1);
        JsonNode place = result.places().get(0);
        assertThat(place.path("name").asText()).isEqualTo("Hinjewadi");
        assertThat(place.path("postcode").asText()).isEqualTo("411057");
        assertThat(logs(appender)).doesNotContain(TEST_KEY);
        detach(appender);
    }

    @Test
    void malformedBodyDoesNotThrow() {
        server.expect(requestTo(containsString("autocomplete")))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        GeoapifyAutocompleteClient.ProviderResult result = client.autocomplete("Hinjewadi");

        assertThat(result.available()).isTrue();
        assertThat(result.places()).isEmpty();
    }

    @Test
    void unauthorizedDoesNotExposeTheKey() {
        assertUnavailable(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void forbiddenDoesNotExposeTheKey() {
        assertUnavailable(HttpStatus.FORBIDDEN);
    }

    @Test
    void tooManyRequestsDoesNotExposeTheKey() {
        assertUnavailable(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void serverErrorDoesNotExposeTheKey() {
        server.expect(requestTo(containsString("autocomplete"))).andRespond(withServerError());
        ListAppender<ILoggingEvent> appender = attachLogs();

        GeoapifyAutocompleteClient.ProviderResult result = client.autocomplete("Aundh");

        assertThat(result.available()).isFalse();
        assertThat(logs(appender)).doesNotContain(TEST_KEY);
        detach(appender);
    }

    @Test
    void timeoutDoesNotExposeTheKey() {
        server.expect(requestTo(containsString("autocomplete")))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));
        ListAppender<ILoggingEvent> appender = attachLogs();

        GeoapifyAutocompleteClient.ProviderResult result = client.autocomplete("Kharadi");

        assertThat(result.available()).isFalse();
        String logged = logs(appender);
        assertThat(logged).contains("status=timeout");
        assertThat(logged).doesNotContain(TEST_KEY);
        detach(appender);
    }

    @Test
    void missingKeyDoesNotCallTheProvider() {
        properties.setApiKey(" ");
        GeoapifyAutocompleteClient.ProviderResult result = client.autocomplete("Hinjewadi");
        assertThat(result.available()).isFalse();
    }

    private void assertUnavailable(HttpStatus status) {
        server.expect(requestTo(containsString("autocomplete")))
                .andRespond(withStatus(status).body("{\"message\":\"" + TEST_KEY + "\"}"));
        ListAppender<ILoggingEvent> appender = attachLogs();

        GeoapifyAutocompleteClient.ProviderResult result = client.autocomplete("411057");

        assertThat(result.available()).isFalse();
        assertThat(result.places()).isEmpty();
        assertThat(logs(appender)).doesNotContain(TEST_KEY);
        detach(appender);
    }

    private static String sampleBody() {
        return """
                {"results":[{"place_id":"place-1","name":"Hinjewadi","city":"Pune","state":"Maharashtra","postcode":"411057","country":"India","country_code":"in","lat":18.59,"lon":73.73}]}
                """;
    }

    private static ListAppender<ILoggingEvent> attachLogs() {
        Logger logger = (Logger) LoggerFactory.getLogger(GeoapifyAutocompleteClient.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static void detach(ListAppender<ILoggingEvent> appender) {
        ((Logger) LoggerFactory.getLogger(GeoapifyAutocompleteClient.class)).detachAppender(appender);
    }

    private static String logs(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
    }
}
