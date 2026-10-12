package com.acomi.acomi_backend.space.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DiscoverListingTextTest {

    @Test
    void sharing_combinedSlashValueMatchesEachListedOption() {
        assertThat(DiscoverListingText.matchesSharing("2/3 Sharing", SharingOccupancy.TWO)).isTrue();
        assertThat(DiscoverListingText.matchesSharing("2/3 Sharing", SharingOccupancy.THREE)).isTrue();
        assertThat(DiscoverListingText.matchesSharing("2/3 Sharing", SharingOccupancy.SINGLE)).isFalse();
        assertThat(DiscoverListingText.matchesSharing("2/3 Sharing", SharingOccupancy.SIX_PLUS)).isFalse();
    }

    @Test
    void sharing_doesNotTreatALargerNumberAsASmallerBucket() {
        assertThat(DiscoverListingText.matchesSharing("12 sharing", SharingOccupancy.TWO)).isFalse();
        assertThat(DiscoverListingText.matchesSharing("12 sharing", SharingOccupancy.SIX_PLUS)).isTrue();
        assertThat(DiscoverListingText.matchesSharing("6+ sharing", SharingOccupancy.SIX_PLUS)).isTrue();
        assertThat(DiscoverListingText.matchesSharing("Single", SharingOccupancy.SINGLE)).isTrue();
    }

    @Test
    void sharing_blankTextMatchesNothing() {
        assertThat(DiscoverListingText.matchesSharing(null, SharingOccupancy.SINGLE)).isFalse();
        assertThat(DiscoverListingText.matchesSharing("-", SharingOccupancy.TWO)).isFalse();
        assertThat(DiscoverListingText.matchesSharing("2", SharingOccupancy.TWO)).isFalse();
    }

    @Test
    void furnishing_requiresAnExplicitPhrase() {
        assertThat(DiscoverListingText.matchesFurnishing("Fully furnished rooms", FurnishingLevel.FULL)).isTrue();
        assertThat(DiscoverListingText.matchesFurnishing("Semi-furnished", FurnishingLevel.SEMI)).isTrue();
        assertThat(DiscoverListingText.matchesFurnishing("Semi-furnished", FurnishingLevel.FULL)).isFalse();
        assertThat(DiscoverListingText.matchesFurnishing("Unfurnished", FurnishingLevel.UNFURNISHED)).isTrue();
        assertThat(DiscoverListingText.matchesFurnishing(null, FurnishingLevel.FULL)).isFalse();
    }

    @Test
    void foodAvailable_onlyMatchesExplicitAvailableWording() {
        assertThat(DiscoverListingText.matchesFoodAvailable("Food optional")).isTrue();
        assertThat(DiscoverListingText.matchesFoodAvailable("No")).isFalse();
        assertThat(DiscoverListingText.matchesFoodAvailable(null)).isFalse();
    }

    @Test
    void servicedApartment_doesNotMatchAGenericFoodService() {
        assertThat(DiscoverListingText.matchesServicedApartment("Serviced apartment")).isTrue();
        assertThat(DiscoverListingText.matchesServicedApartment("Corporate apartments")).isTrue();
        assertThat(DiscoverListingText.matchesServicedApartment("PG / Food Service")).isFalse();
    }
}
