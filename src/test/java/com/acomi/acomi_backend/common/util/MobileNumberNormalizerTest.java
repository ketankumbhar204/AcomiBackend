package com.acomi.acomi_backend.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acomi.acomi_backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class MobileNumberNormalizerTest {

    @Test
    void normalize_stripsCountryCodeAndFormatting() {
        assertThat(MobileNumberNormalizer.normalize("9876543210")).isEqualTo("9876543210");
        assertThat(MobileNumberNormalizer.normalize("+91 98765 43210")).isEqualTo("9876543210");
        assertThat(MobileNumberNormalizer.normalize("919876543210")).isEqualTo("9876543210");
    }

    @Test
    void normalize_rejectsInvalidNumbers() {
        assertThatThrownBy(() -> MobileNumberNormalizer.normalize("12345"))
                .isInstanceOf(BusinessException.class);
    }
}
