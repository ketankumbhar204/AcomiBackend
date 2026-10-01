package com.acomi.acomi_backend.location.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.acomi.acomi_backend.location.api.dto.response.LocationAutocompleteSuggestion;
import com.acomi.acomi_backend.location.application.support.LocationAutocompleteMatcher;
import com.acomi.acomi_backend.location.config.GeoapifyProperties;
import com.acomi.acomi_backend.location.infrastructure.geoapify.GeoapifyAutocompleteClient;
import com.acomi.acomi_backend.location.infrastructure.geoapify.GeoapifyAutocompleteClient.ProviderResult;
import com.acomi.acomi_backend.storage.config.StorageProperties;
import com.acomi.acomi_backend.storage.infrastructure.provider.memory.InMemoryStorageProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class LocationAutocompleteServiceTest {

    private static final String TEST_KEY = "unit-test-geoapify-key";
    private static final String SAMPLE = """
            [
              {"officeName":"Aundh S.O","pincode":411007,"taluk":"Haveli","districtName":"Pune","stateName":"MAHARASHTRA"},
              {"officeName":"Aundh B.O","pincode":422510,"taluk":"Kopargaon","districtName":"Ahmednagar","stateName":"MAHARASHTRA"},
              {"officeName":"Hinjewadi S.O","pincode":411057,"taluk":"Mulshi","districtName":"Pune","stateName":"MAHARASHTRA"}
            ]
            """;

    @Mock
    private GeoapifyAutocompleteClient client;

    private GeoapifyProperties properties;
    private LocationReferenceService locations;
    private LocationAutocompleteService service;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        properties = new GeoapifyProperties();
        properties.setApiKey(TEST_KEY);
        properties.setLimit(8);
        properties.setMinQueryLength(3);
        properties.setCacheTtlSeconds(300);
        InMemoryStorageProvider storage = new InMemoryStorageProvider();
        StorageProperties storageProperties = new StorageProperties();
        storageProperties.setBucket("acomi-files");
        byte[] bytes = SAMPLE.getBytes(StandardCharsets.UTF_8);
        storage.putStream(
                "acomi-files",
                LocationReferenceService.OBJECT_KEY,
                new ByteArrayInputStream(bytes),
                bytes.length,
                "application/json");
        locations = new LocationReferenceService(storage, storageProperties);
        service = new LocationAutocompleteService(properties, client, new LocationAutocompleteMatcher(locations));
        mapper = new ObjectMapper();
    }

    @Test
    void emptyAndShortQueriesDoNotCallTheProvider() {
        assertThat(service.autocomplete(null, null, null)).isEmpty();
        assertThat(service.autocomplete("  ", null, null)).isEmpty();
        assertThat(service.autocomplete(" a ", null, null)).isEmpty();
        assertThat(service.autocomplete("au", null, null)).isEmpty();
        verify(client, never()).autocomplete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingKeyReturnsEmptyAndDoesNotCallTheProvider() {
        properties.setApiKey("");
        ListAppender<ILoggingEvent> appender = attachLogs();

        assertThat(service.autocomplete("Hinjewadi", null, null)).isEmpty();

        verify(client, never()).autocomplete(org.mockito.ArgumentMatchers.any());
        assertThat(logs(appender)).doesNotContain(TEST_KEY);
        detach(appender);
    }

    @Test
    void mapsAMatchAndDropsFieldsOutsideIndia() throws Exception {
        when(client.autocomplete("Hinjewadi")).thenReturn(ProviderResult.success(List.of(
                place("""
                        {"place_id":"p1","name":"Hinjewadi","suburb":"Hinjewadi","city":"Pune","county":"Pune","state":"Maharashtra","postcode":"411057","country":"India","country_code":"in","lat":18.59,"lon":73.73,"formatted":"Hinjewadi, Pune, Maharashtra, India"}
                        """),
                place("""
                        {"place_id":"abroad","name":"Hinjewadi","city":"London","country":"United Kingdom","country_code":"gb","lat":51.5,"lon":-0.1}
                        """),
                place("""
                        {"name":"Broken","city":"Pune","country_code":"in","lat":"nope","lon":null,"postcode":"12"}
                        """))));

        List<LocationAutocompleteSuggestion> suggestions = service.autocomplete("Hinjewadi", null, null);

        assertThat(suggestions).hasSize(2);
        LocationAutocompleteSuggestion hinjewadi = suggestions.get(0);
        assertThat(hinjewadi.getSource()).isEqualTo("GEOAPIFY");
        assertThat(hinjewadi.isMatched()).isTrue();
        assertThat(hinjewadi.getPincode()).isEqualTo("411057");
        assertThat(hinjewadi.getState()).isEqualTo("MAHARASHTRA");
        assertThat(hinjewadi.getTaluka()).isEqualTo("Mulshi");
        assertThat(hinjewadi.getAcomiLocation().getLocation()).isEqualTo("Hinjewadi");
        assertThat(hinjewadi.getLatitude()).isEqualTo(18.59);
        assertThat(suggestions.get(1).getLatitude()).isNull();
        assertThat(suggestions.get(1).getPincode()).isNull();
        assertThat(suggestions.get(1).getCity()).isEqualTo("Pune");
        assertThat(suggestions.toString()).doesNotContain(TEST_KEY);
        assertThat(locations.listAreas("MAHARASHTRA", "Pune", "Mulshi")).hasSize(1);
    }

    @Test
    void ambiguousAundhIsNotMerged() throws Exception {
        when(client.autocomplete("Aundh")).thenReturn(ProviderResult.success(List.of(place("""
                {"name":"Aundh","city":"Pune","state":"Maharashtra","country_code":"in","formatted":"Aundh, Maharashtra, India"}
                """))));

        List<LocationAutocompleteSuggestion> suggestions = service.autocomplete("Aundh", "MAHARASHTRA", "Pune");

        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.get(0).isMatched()).isFalse();
        assertThat(suggestions.get(0).getAcomiLocation()).isNull();
        assertThat(suggestions.get(0).getPincode()).isNull();
    }

    @Test
    void providerFailureReturnsEmpty() {
        when(client.autocomplete("Kharadi")).thenReturn(ProviderResult.unavailable());
        assertThat(service.autocomplete("Kharadi", null, null)).isEmpty();
    }

    @Test
    void resultLimitIsEight() throws Exception {
        properties.setLimit(8);
        List<JsonNode> places = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            places.add(place("{\"name\":\"Place " + i + "\",\"country_code\":\"in\",\"city\":\"Pune\"}"));
        }
        when(client.autocomplete("Pune area")).thenReturn(ProviderResult.success(places));

        assertThat(service.autocomplete("Pune area", null, null)).hasSize(8);
    }

    @Test
    void repeatedQueryUsesTheCache() throws Exception {
        when(client.autocomplete("411057")).thenReturn(ProviderResult.success(List.of(place("""
                {"name":"Hinjewadi","postcode":"411057","city":"Pune","county":"Pune","state":"Maharashtra","country_code":"in"}
                """))));

        assertThat(service.autocomplete("411057", null, null)).hasSize(1);
        assertThat(service.autocomplete("411057", null, null)).hasSize(1);
        verify(client, org.mockito.Mockito.times(1)).autocomplete("411057");
    }

    private JsonNode place(String json) throws Exception {
        return mapper.readTree(json);
    }

    private static ListAppender<ILoggingEvent> attachLogs() {
        Logger logger = (Logger) LoggerFactory.getLogger(LocationAutocompleteService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static void detach(ListAppender<ILoggingEvent> appender) {
        ((Logger) LoggerFactory.getLogger(LocationAutocompleteService.class)).detachAppender(appender);
    }

    private static String logs(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
    }
}
