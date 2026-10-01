package com.acomi.acomi_backend.location.infrastructure.geoapify;

import com.acomi.acomi_backend.location.config.GeoapifyProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Server-side Geoapify Address Autocomplete client. The request URI contains the API key
 * and must never be logged.
 */
@Component
public class GeoapifyAutocompleteClient {

    private static final Logger log = LoggerFactory.getLogger(GeoapifyAutocompleteClient.class);

    private final GeoapifyProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public GeoapifyAutocompleteClient(GeoapifyProperties properties) {
        this(properties, createRestClient(properties), new ObjectMapper());
    }

    GeoapifyAutocompleteClient(GeoapifyProperties properties, RestClient restClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    static RestClient createRestClient(GeoapifyProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeout = Math.max(500, properties.getTimeoutMs());
        factory.setConnectTimeout(Duration.ofMillis(timeout));
        factory.setReadTimeout(Duration.ofMillis(timeout));
        return RestClient.builder().requestFactory(factory).build();
    }

    public ProviderResult autocomplete(String text) {
        if (!StringUtils.hasText(properties.getApiKey()) || !StringUtils.hasText(text)) {
            return ProviderResult.unavailable();
        }
        long started = System.nanoTime();
        try {
            String body = restClient.get().uri(requestUri(text)).retrieve().body(String.class);
            long elapsedMs = elapsed(started);
            log.info(
                    "geoapify_autocomplete_ok elapsedMs={} queryLength={} status=200",
                    elapsedMs,
                    text.length());
            return ProviderResult.success(readPlaces(body));
        } catch (RestClientResponseException ex) {
            log.warn(
                    "geoapify_autocomplete_failed elapsedMs={} queryLength={} status={}",
                    elapsed(started),
                    text.length(),
                    ex.getStatusCode().value());
            return ProviderResult.unavailable();
        } catch (ResourceAccessException ex) {
            log.warn(
                    "geoapify_autocomplete_failed elapsedMs={} queryLength={} status=timeout",
                    elapsed(started),
                    text.length());
            return ProviderResult.unavailable();
        } catch (RestClientException ex) {
            log.warn(
                    "geoapify_autocomplete_failed elapsedMs={} queryLength={} status=error",
                    elapsed(started),
                    text.length());
            return ProviderResult.unavailable();
        }
    }

    URI requestUri(String text) {
        int providerLimit = Math.min(20, Math.max(1, properties.getLimit()) * 3);
        String country = StringUtils.hasText(properties.getCountryCode())
                ? properties.getCountryCode().trim().toLowerCase()
                : "in";
        String base = StringUtils.hasText(properties.getBaseUrl())
                ? properties.getBaseUrl().trim()
                : "https://api.geoapify.com";
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(base)
                .path("/v1/geocode/autocomplete")
                .queryParam("text", text);
        if (text.matches("\\d{6}")) {
            builder.queryParam("type", "postcode");
        } else if (!text.contains(" ")) {
            builder.queryParam("type", "locality");
        }
        return builder
                .queryParam("filter", "countrycode:" + country)
                .queryParam("bias", "countrycode:" + country)
                .queryParam("limit", providerLimit)
                .queryParam("format", "json")
                .queryParam("apiKey", properties.getApiKey().trim())
                .build()
                .encode()
                .toUri();
    }

    private List<JsonNode> readPlaces(String body) {
        if (!StringUtils.hasText(body)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode results = root.path("results");
            if (results.isArray()) {
                return copy(results);
            }
            JsonNode features = root.path("features");
            if (features.isArray()) {
                List<JsonNode> places = new ArrayList<>();
                for (JsonNode feature : features) {
                    JsonNode propertiesNode = feature.path("properties");
                    if (propertiesNode.isObject()) {
                        places.add(propertiesNode);
                    }
                }
                return places;
            }
            return List.of();
        } catch (Exception ex) {
            log.warn("geoapify_autocomplete_failed status=malformed queryLength=0");
            return List.of();
        }
    }

    private static List<JsonNode> copy(JsonNode array) {
        List<JsonNode> places = new ArrayList<>();
        array.forEach(places::add);
        return places;
    }

    private static long elapsed(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    public record ProviderResult(boolean available, List<JsonNode> places) {
        public static ProviderResult success(List<JsonNode> places) {
            return new ProviderResult(true, places == null ? List.of() : List.copyOf(places));
        }

        public static ProviderResult unavailable() {
            return new ProviderResult(false, List.of());
        }
    }
}
