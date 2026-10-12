package com.acomi.acomi_backend.property.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.space.domain.model.SpaceType;
import org.junit.jupiter.api.Test;

class PriceBasisTest {

    @Test
    void defaultsFollowThePropertyType() {
        assertThat(PriceBasis.forPropertyType(SpaceType.PG)).isEqualTo(PriceBasis.PER_BED);
        assertThat(PriceBasis.forPropertyType(SpaceType.HOSTEL)).isEqualTo(PriceBasis.PER_BED);
        assertThat(PriceBasis.forPropertyType(SpaceType.CO_LIVING)).isEqualTo(PriceBasis.PER_ROOM);
        assertThat(PriceBasis.forPropertyType(SpaceType.RENTAL)).isEqualTo(PriceBasis.PER_UNIT);
        assertThat(PriceBasis.forPropertyType(SpaceType.MESS)).isEqualTo(PriceBasis.PER_UNIT);
    }

    @Test
    void requestedUnitWinsAndAMissingUnitKeepsTheTypeDefault() {
        assertThat(PriceBasis.resolve(SpaceType.PG, PriceBasis.PER_ROOM)).isEqualTo(PriceBasis.PER_ROOM);
        assertThat(PriceBasis.resolve(SpaceType.CO_LIVING, null)).isEqualTo(PriceBasis.PER_ROOM);
        assertThat(PriceBasis.resolve(SpaceType.MESS, null)).isEqualTo(PriceBasis.PER_UNIT);
    }
}
