package com.amico.amico_backend.accommodation.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.amico.amico_backend.accommodation.domain.model.BedLabelStyle;
import java.util.HashSet;
import java.util.List;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccommodationNumberingServiceTest {

    private final AccommodationNumberingService numberingService = new AccommodationNumberingService();

    @Test
    void pgRoomNumber_followsFloorPrefixFormula() {
        assertThat(numberingService.pgRoomNumber(0, 1)).isEqualTo("101");
        assertThat(numberingService.pgRoomNumber(0, 10)).isEqualTo("110");
        assertThat(numberingService.pgRoomNumber(1, 1)).isEqualTo("201");
    }

    @Test
    void bedLabels_alphaThenNumericFallback() {
        List<String> labels = numberingService.bedLabels(3, BedLabelStyle.ALPHA, new HashSet<>());
        assertThat(labels).containsExactly("A", "B", "C");
    }

    @Test
    void coLivingRoomLabels_usesLetters() {
        assertThat(numberingService.coLivingRoomLabels(3)).containsExactly("A", "B", "C");
    }

    @Test
    void allocateUnitNumbers_usesStartNumberWhenProvided() {
        assertThat(numberingService.allocateUnitNumbers(3, "101", List.of()))
                .containsExactly("101", "102", "103");
    }
}
