package com.acomi.acomi_backend.location.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Optional Geoapify address autocomplete. The API key is {@code GEOAPIFY_API_KEY}.
 * A missing key must not stop startup; autocomplete then returns no suggestions.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "acomi.geoapify")
public class GeoapifyProperties {

    /** Provider API key. Never log or return this value. */
    private String apiKey = "";

    private String baseUrl = "https://api.geoapify.com";

    private int timeoutMs = 3_000;

    /** Compact suggestion list. Geoapify is asked for this many results. */
    private int limit = 8;

    private int minQueryLength = 3;

    /** Short in-memory TTL for repetitive queries. Not persisted. */
    private int cacheTtlSeconds = 300;

    private int cacheMaxEntries = 200;

    /** Geoapify country filter. India only. */
    private String countryCode = "in";
}
