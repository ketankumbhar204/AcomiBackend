package com.acomi.acomi_backend.space.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DiscoverListingSanitizerTest {

    @Test
    void text_stripsPlaceholdersAndBlank() {
        assertThat(DiscoverListingSanitizer.text(null)).isNull();
        assertThat(DiscoverListingSanitizer.text("  ")).isNull();
        assertThat(DiscoverListingSanitizer.text("—")).isNull();
        assertThat(DiscoverListingSanitizer.text("-")).isNull();
        assertThat(DiscoverListingSanitizer.text("Wakad")).isEqualTo("Wakad");
        assertThat(DiscoverListingSanitizer.text("  Pune  ")).isEqualTo("Pune");
    }

    @Test
    void coordinates_rejectOutOfRange() {
        assertThat(DiscoverListingSanitizer.latitude(new BigDecimal("91"))).isNull();
        assertThat(DiscoverListingSanitizer.latitude(new BigDecimal("-91"))).isNull();
        assertThat(DiscoverListingSanitizer.longitude(new BigDecimal("181"))).isNull();
        assertThat(DiscoverListingSanitizer.longitude(new BigDecimal("-181"))).isNull();
        assertThat(DiscoverListingSanitizer.latitude(new BigDecimal("18.6052262")))
                .isEqualByComparingTo("18.6052262");
        assertThat(DiscoverListingSanitizer.longitude(new BigDecimal("73.7236231")))
                .isEqualByComparingTo("73.7236231");
    }

    @Test
    void firstCoordinate_fallsBackWhenPrimaryMissing() {
        assertThat(DiscoverListingSanitizer.firstLatitude(null, new BigDecimal("18.6")))
                .isEqualByComparingTo("18.6");
        assertThat(DiscoverListingSanitizer.firstLatitude(new BigDecimal("18.1"), new BigDecimal("18.9")))
                .isEqualByComparingTo("18.1");
    }

    @Test
    void positivePrice_omitsZeroAndNegative() {
        assertThat(DiscoverListingSanitizer.positivePrice(null)).isNull();
        assertThat(DiscoverListingSanitizer.positivePrice(BigDecimal.ZERO)).isNull();
        assertThat(DiscoverListingSanitizer.positivePrice(new BigDecimal("-1"))).isNull();
        assertThat(DiscoverListingSanitizer.positivePrice(new BigDecimal("8500.00")))
                .isEqualByComparingTo("8500.00");
    }

    @Test
    void mapUrl_allowsHttpOnly() {
        assertThat(DiscoverListingSanitizer.mapUrl("https://maps.google.com/?q=18.6,73.7"))
                .isEqualTo("https://maps.google.com/?q=18.6,73.7");
        assertThat(DiscoverListingSanitizer.mapUrl("http://maps.google.com/?q=18.6,73.7"))
                .startsWith("http://");
        assertThat(DiscoverListingSanitizer.mapUrl("javascript:alert(1)")).isNull();
        assertThat(DiscoverListingSanitizer.mapUrl("not a url")).isNull();
        assertThat(DiscoverListingSanitizer.mapUrl(null)).isNull();
    }
}
