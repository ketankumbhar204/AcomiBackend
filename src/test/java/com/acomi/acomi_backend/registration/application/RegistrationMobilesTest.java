package com.acomi.acomi_backend.registration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acomi.acomi_backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RegistrationMobilesTest {

    @Test
    void normalizeOptional_blankBecomesNull() {
        assertThat(RegistrationMobiles.normalizeOptional(null)).isNull();
        assertThat(RegistrationMobiles.normalizeOptional("")).isNull();
        assertThat(RegistrationMobiles.normalizeOptional("   ")).isNull();
    }

    @Test
    void normalizeOptional_acceptsValidIndianMobile() {
        assertThat(RegistrationMobiles.normalizeOptional(" 9123456780 ")).isEqualTo("9123456780");
    }

    @Test
    void normalizeOptional_rejectsInvalidMobile() {
        assertThatThrownBy(() -> RegistrationMobiles.normalizeOptional("12345"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void resolveAlternate_allowsPrimaryOnly() {
        assertThat(RegistrationMobiles.resolveAlternate("9876543210", null)).isNull();
        assertThat(RegistrationMobiles.resolveAlternate("9876543210", "")).isNull();
    }

    @Test
    void resolveAlternate_rejectsSameAsPrimary() {
        assertThatThrownBy(() -> RegistrationMobiles.resolveAlternate("9876543210", "9876543210"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("different from the primary");
    }

    @Test
    void resolveAlternate_keepsDistinctNumber() {
        assertThat(RegistrationMobiles.resolveAlternate("9876543210", "9123456780")).isEqualTo("9123456780");
    }
}
