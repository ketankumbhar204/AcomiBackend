package com.acomi.acomi_backend.location.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LocationOfficeNameNormalizerTest {

    @Test
    void stripsSoAndTrailingParenthetical() {
        String original = "Yeshwantnagar S.O (Solapur)";
        assertThat(LocationOfficeNameNormalizer.toDisplayName(original)).isEqualTo("Yeshwantnagar");
        assertThat(original).isEqualTo("Yeshwantnagar S.O (Solapur)");
    }

    @Test
    void stripsBhainsaSo() {
        assertThat(LocationOfficeNameNormalizer.toDisplayName("Bhainsa S.O (Adilabad)")).isEqualTo("Bhainsa");
    }

    @Test
    void stripsBoSuffix() {
        assertThat(LocationOfficeNameNormalizer.toDisplayName("Example B.O")).isEqualTo("Example");
        assertThat(LocationOfficeNameNormalizer.toDisplayName("Ada B.O")).isEqualTo("Ada");
    }

    @Test
    void stripsHoSuffix() {
        assertThat(LocationOfficeNameNormalizer.toDisplayName("Example H.O")).isEqualTo("Example");
        assertThat(LocationOfficeNameNormalizer.toDisplayName("Pune H.O.")).isEqualTo("Pune");
    }

    @Test
    void preservesMeaningfulParentheticalBeforeSuffix() {
        assertThat(LocationOfficeNameNormalizer.toDisplayName("Arli (T) B.O")).isEqualTo("Arli (T)");
    }

    @Test
    void doesNotStripEmbeddedSoText() {
        assertThat(LocationOfficeNameNormalizer.toDisplayName("South Office Campus")).isEqualTo("South Office Campus");
    }

    @Test
    void handlesBlankAndNull() {
        assertThat(LocationOfficeNameNormalizer.toDisplayName(null)).isEqualTo("");
        assertThat(LocationOfficeNameNormalizer.toDisplayName("   ")).isEqualTo("");
    }
}
