package com.acomi.acomi_backend.common.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BusinessReferenceCodesTest {

    @Test
    void formatsAtLeastFiveDigitsAndExpands() {
        assertThat(BusinessReferenceCodes.format(BusinessReferencePrefix.USR, 1)).isEqualTo("USR00001");
        assertThat(BusinessReferenceCodes.format(BusinessReferencePrefix.SPC, 99999)).isEqualTo("SPC99999");
        assertThat(BusinessReferenceCodes.format(BusinessReferencePrefix.BED, 100000)).isEqualTo("BED100000");
    }

    @Test
    void rejectsNonPositiveSequences() {
        assertThatThrownBy(() -> BusinessReferenceCodes.format(BusinessReferencePrefix.MEM, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizesCaseAndParsesKnownPrefixesOnly() {
        assertThat(BusinessReferenceCodes.normalize(" usr00012 ")).isEqualTo("USR00012");
        assertThat(BusinessReferenceCodes.prefixOf("ENQ00001")).contains(BusinessReferencePrefix.ENQ);
        assertThat(BusinessReferenceCodes.prefixOf("PAY-20260720-000123")).isEmpty();
        assertThat(BusinessReferenceCodes.prefixOf("PR-2026-000001")).isEmpty();
        assertThat(BusinessReferenceCodes.isCanonical("SPC00001")).isTrue();
    }
}
