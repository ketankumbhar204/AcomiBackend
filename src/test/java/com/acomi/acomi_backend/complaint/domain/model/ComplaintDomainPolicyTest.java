package com.acomi.acomi_backend.complaint.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ComplaintDomainPolicyTest {

    @ParameterizedTest
    @CsvSource({
        "OPEN, IN_PROGRESS, true",
        "OPEN, RESOLVED, true",
        "OPEN, CANCELLED, true",
        "OPEN, CLOSED, false",
        "IN_PROGRESS, RESOLVED, true",
        "IN_PROGRESS, OPEN, true",
        "IN_PROGRESS, CLOSED, false",
        "RESOLVED, CLOSED, true",
        "RESOLVED, OPEN, false",
        "CLOSED, OPEN, false",
        "CANCELLED, OPEN, false"
    })
    void statusTransitions(ComplaintStatus from, ComplaintStatus to, boolean allowed) {
        assertThat(ComplaintStatusTransition.canTransition(from, to)).isEqualTo(allowed);
    }

    @Test
    void reopenWithinWindow() {
        LocalDateTime resolvedAt = LocalDateTime.of(2026, 7, 1, 10, 0);
        assertThat(ComplaintReopenPolicy.canReopen(
                        ComplaintStatus.RESOLVED, resolvedAt, resolvedAt.plusDays(7)))
                .isTrue();
        assertThat(ComplaintReopenPolicy.canReopen(
                        ComplaintStatus.RESOLVED, resolvedAt, resolvedAt.plusDays(7).plusSeconds(1)))
                .isFalse();
        assertThat(ComplaintReopenPolicy.canReopen(ComplaintStatus.CLOSED, resolvedAt, resolvedAt.plusDays(1)))
                .isFalse();
        assertThat(ComplaintReopenPolicy.canReopen(ComplaintStatus.OPEN, resolvedAt, resolvedAt.plusDays(1)))
                .isFalse();
    }

    @Test
    void categoriesBySpaceType() {
        assertThat(ComplaintCategory.allowedFor(SpaceType.PG))
                .contains(ComplaintCategory.MAINTENANCE, ComplaintCategory.HOUSEKEEPING)
                .doesNotContain(ComplaintCategory.FOOD_QUALITY);
        assertThat(ComplaintCategory.allowedFor(SpaceType.MESS))
                .contains(ComplaintCategory.FOOD_QUALITY, ComplaintCategory.FOOD_SERVICE)
                .doesNotContain(ComplaintCategory.MAINTENANCE);
    }
}
