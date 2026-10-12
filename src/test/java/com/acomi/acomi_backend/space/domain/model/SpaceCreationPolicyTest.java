package com.acomi.acomi_backend.space.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SpaceCreationPolicyTest {

    @Test
    void tenantOrCustomerCannotCreate() {
        assertThat(SpaceCreationPolicy.mayCreateSpace(true, false)).isFalse();
    }

    @Test
    void ownerCanCreateAnotherSpace() {
        assertThat(SpaceCreationPolicy.mayCreateSpace(true, true)).isTrue();
    }

    @Test
    void accountWithNoMembershipCanCreateTheFirstSpace() {
        assertThat(SpaceCreationPolicy.mayCreateSpace(false, false)).isTrue();
    }
}
