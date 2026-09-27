package com.acomi.acomi_backend.space.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LocationFilterTokensTest {

    @Test
    void exactLocation_matchesAddressContainingTerm() {
        assertThat(LocationFilterTokens.addressContains("Aundh Road, Pune", "Aundh")).isTrue();
    }

    @Test
    void exactLocation_doesNotMatchUnrelatedAddress() {
        assertThat(LocationFilterTokens.addressContains("Baner Road, Pune", "Aundh")).isFalse();
    }

    @Test
    void parentheticalLocation_matchesHinjewadiInfotechParkAddress() {
        assertThat(LocationFilterTokens.addressContains(
                        "Hinjewadi Rajiv Gandhi Infotech Park, Pune", "Infotech Park (Hinjawadi)"))
                .isTrue();
    }

    @Test
    void hinjawadiSelection_matchesHinjewadiSpellingInAddress() {
        assertThat(LocationFilterTokens.addressContains(
                        "Hinjewadi Rajiv Gandhi Infotech Park, Pune", "Hinjawadi"))
                .isTrue();
    }

    @Test
    void hinjewadiSelection_matchesHinjawadiSpellingInAddress() {
        assertThat(LocationFilterTokens.addressContains(
                        "Hinjawadi Rajiv Gandhi Infotech Park, Pune", "Hinjewadi"))
                .isTrue();
    }

    @Test
    void parentheticalLocation_doesNotMatchMerelyBecauseAddressContainsPune() {
        assertThat(LocationFilterTokens.addressContains("Baner Road, Pune", "Infotech Park (Hinjawadi)"))
                .isFalse();
        assertThat(LocationFilterTokens.needles("Infotech Park (Hinjawadi)")).doesNotContain("pune", "maharashtra");
        assertThat(LocationFilterTokens.needles("Example Area (Pune)")).containsExactly("example area");
    }

    @Test
    void pincodeIsNotUsedForMatching() {
        assertThat(LocationFilterTokens.addressContains(
                        "Hinjewadi Rajiv Gandhi Infotech Park, 110001", "Infotech Park (Hinjawadi)"))
                .isTrue();
        assertThat(LocationFilterTokens.addressContains("Some other street, 411057", "Infotech Park (Hinjawadi)"))
                .isFalse();
        assertThat(LocationFilterTokens.addressContains("Baner Road, 411007", "Aundh")).isFalse();
    }

    @Test
    void infotechParkHinjawadi_extractsParkAndLocalityAliases() {
        assertThat(LocationFilterTokens.needles("Infotech Park (Hinjawadi)"))
                .containsExactly("infotech park", "hinjawadi", "hinjewadi");
    }

    @Test
    void simpleLocation_isUnchanged() {
        assertThat(LocationFilterTokens.needles("Aundh")).containsExactly("aundh");
        assertThat(LocationFilterTokens.needles("Balewadi")).containsExactly("balewadi");
        assertThat(LocationFilterTokens.needles("Hadapsar")).containsExactly("hadapsar");
    }

    @Test
    void aliasesAreExplicitAndBidirectional() {
        assertThat(LocationLocalityAliases.expand("hinjawadi")).containsExactly("hinjawadi", "hinjewadi");
        assertThat(LocationLocalityAliases.expand("hinjewadi")).containsExactly("hinjewadi", "hinjawadi");
        assertThat(LocationLocalityAliases.expand("aundh")).containsExactly("aundh");
    }

    @Test
    void contextDistrictIsNotUsedAsAStandaloneNeedle() {
        assertThat(LocationFilterTokens.needles("Infotech Park (Hinjawadi)", "Pune", "MAHARASHTRA"))
                .containsExactly("infotech park", "hinjawadi", "hinjewadi");
    }

    @Test
    void balewadiStillMatchesBalewadiHighStreet() {
        assertThat(LocationFilterTokens.addressContains("Balewadi High Street, Pune", "Balewadi")).isTrue();
    }
}
