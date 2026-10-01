package com.acomi.acomi_backend.space.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LocationLocalityAliasesTest {

    @Test
    void hinjawadiGroup_isTheOnlyConfiguredAlias() {
        assertThat(LocationLocalityAliases.groups()).containsExactly(java.util.List.of("hinjawadi", "hinjewadi"));
    }

    @Test
    void multiWordTerm_expandsEmbeddedAliasWord() {
        assertThat(LocationLocalityAliases.expand("rajiv gandhi hinjawadi"))
                .contains("rajiv gandhi hinjawadi", "rajiv gandhi hinjewadi");
    }
}
