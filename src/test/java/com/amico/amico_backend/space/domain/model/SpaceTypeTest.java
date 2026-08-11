package com.amico.amico_backend.space.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SpaceTypeTest {

    @Test
    void shouldIncludeRentalType() {
        assertThat(SpaceType.values()).contains(SpaceType.RENTAL);
    }

    @Test
    void shouldParseRentalFromString() {
        assertThat(SpaceType.valueOf("RENTAL")).isEqualTo(SpaceType.RENTAL);
    }
}
