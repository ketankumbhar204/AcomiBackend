package com.acomi.acomi_backend.config.discovery;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "acomi.discovery")
public class DiscoveryProperties {

    /**
     * When true, member discovery also returns active Spaces converted from test-lead
     * registrations even if {@code discoverable=false}. Must stay false outside local.
     */
    private boolean includeTestSpaces = false;
}
