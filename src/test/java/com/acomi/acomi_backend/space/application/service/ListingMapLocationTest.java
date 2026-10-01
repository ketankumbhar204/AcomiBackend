package com.acomi.acomi_backend.space.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ListingMapLocationTest {

    @Test
    void resolve_buildsMapUrlFromCoordinatesWhenLinkMissing() {
        ListingMapLocation.Resolved resolved =
                ListingMapLocation.resolve(new BigDecimal("18.6052262"), new BigDecimal("73.7236231"), null);

        assertThat(resolved.latitude()).isEqualByComparingTo("18.6052262");
        assertThat(resolved.longitude()).isEqualByComparingTo("73.7236231");
        assertThat(resolved.mapUrl()).isEqualTo("https://maps.google.com/?q=18.6052262,73.7236231");
    }

    @Test
    void resolve_keepsPastedGoogleLinkWhenCoordinatesMissing() {
        String link = "https://maps.app.goo.gl/abcd1234";
        ListingMapLocation.Resolved resolved = ListingMapLocation.resolve(null, null, link);

        assertThat(resolved.mapUrl()).isEqualTo(link);
        assertThat(resolved.latitude()).isNull();
        assertThat(resolved.longitude()).isNull();
    }

    @Test
    void resolve_extractsCoordinatesFromGoogleMapsQuery() {
        ListingMapLocation.Resolved resolved =
                ListingMapLocation.resolve(
                        null, null, "https://www.google.com/maps/search/?api=1&query=18.52,73.85");

        assertThat(resolved.latitude()).isEqualByComparingTo("18.52");
        assertThat(resolved.longitude()).isEqualByComparingTo("73.85");
        assertThat(resolved.mapUrl()).isEqualTo("https://www.google.com/maps/search/?api=1&query=18.52,73.85");
    }

    @Test
    void resolve_keepsBothWhenLinkAndCoordinatesArePresent() {
        String link = "https://maps.google.com/?q=18.6,73.7";
        ListingMapLocation.Resolved resolved =
                ListingMapLocation.resolve(new BigDecimal("18.1"), new BigDecimal("73.2"), link);

        assertThat(resolved.latitude()).isEqualByComparingTo("18.1");
        assertThat(resolved.longitude()).isEqualByComparingTo("73.2");
        assertThat(resolved.mapUrl()).isEqualTo(link);
    }
}
