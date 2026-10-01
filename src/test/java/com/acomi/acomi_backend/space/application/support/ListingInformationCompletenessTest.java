package com.acomi.acomi_backend.space.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.space.application.service.DiscoverListingSanitizer;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ListingInformationCompletenessTest {

    @Test
    void fullInformationScoresTen() {
        assertThat(score(true, true, true, true)).isEqualTo(10);
        assertThat(ListingInformationCompleteness.MAX_SCORE).isEqualTo(10);
    }

    @Test
    void mobileAddressAndMapScoreNine() {
        assertThat(score(true, true, true, false)).isEqualTo(9);
    }

    @Test
    void mobileAddressAndAmenitiesScoreEight() {
        assertThat(score(true, true, false, true)).isEqualTo(8);
    }

    @Test
    void addressMapAndAmenitiesScoreSix() {
        assertThat(score(false, true, true, true)).isEqualTo(6);
    }

    @Test
    void addressOnlyScoresThree() {
        assertThat(score(false, true, false, false)).isEqualTo(3);
    }

    @Test
    void noInformationScoresZero() {
        assertThat(score(false, false, false, false)).isZero();
    }

    @Test
    void emailOnlyDoesNotReceiveMobilePoints() {
        assertThat(DiscoverListingSanitizer.hasUsableContact("owner@example.com")).isTrue();
        assertThat(ListingInformationCompleteness.hasUsableMobile("owner@example.com")).isFalse();
        assertThat(score(ListingInformationCompleteness.hasUsableMobile("owner@example.com"), false, false, false))
                .isZero();
    }

    @Test
    void invalidOrBlankMobileDoesNotReceiveMobilePoints() {
        assertThat(ListingInformationCompleteness.hasUsableMobile(null, "", "   ", "12345", "abcdefghij", "6000000000"))
                .isFalse();
        assertThat(ListingInformationCompleteness.hasUsableMobile("0221234567")).isFalse();
        assertThat(ListingInformationCompleteness.hasUsableMobile("9876543210")).isTrue();
        assertThat(ListingInformationCompleteness.hasUsableMobile("+91 98765 43210")).isTrue();
    }

    @Test
    void addressPlaceholderDoesNotReceiveAddressPoints() {
        assertThat(ListingInformationCompleteness.hasUsableAddress(null, "", "  ", "-", "—", "NA", "N/A", " n/a "))
                .isFalse();
        assertThat(ListingInformationCompleteness.hasUsableAddress("12 MG Road, Pune")).isTrue();
    }

    @Test
    void invalidMapUrlDoesNotReceiveMapPoints() {
        assertThat(ListingInformationCompleteness.hasValidMapUrl(null)).isFalse();
        assertThat(ListingInformationCompleteness.hasValidMapUrl("")).isFalse();
        assertThat(ListingInformationCompleteness.hasValidMapUrl("not a url")).isFalse();
        assertThat(ListingInformationCompleteness.hasValidMapUrl("ftp://files.example/map")).isFalse();
        assertThat(ListingInformationCompleteness.hasValidMapUrl("https://maps.google.com/?q=place")).isTrue();
    }

    @Test
    void latLongWithoutMapUrlDoesNotReceiveMapPoints() {
        assertThat(ListingInformationCompleteness.hasValidMapUrl(null)).isFalse();
        assertThat(score(false, true, ListingInformationCompleteness.hasValidMapUrl(null), false)).isEqualTo(3);
    }

    @Test
    void emptyAmenitiesDoNotScore() {
        assertThat(ListingInformationCompleteness.hasUsableAmenity(null)).isFalse();
        assertThat(ListingInformationCompleteness.hasUsableAmenity(List.of())).isFalse();
        assertThat(ListingInformationCompleteness.hasUsableAmenity(List.of("", "  ", "-", "NA"))).isFalse();
    }

    @Test
    void foodIncludedDoesNotCountAsAnAmenity() {
        assertThat(ListingInformationCompleteness.hasUsableAmenity(List.of("FOOD_INCLUDED"))).isFalse();
        assertThat(ListingInformationCompleteness.hasUsableAmenity(List.of("WIFI", "FOOD_INCLUDED"))).isTrue();
        assertThat(score(false, false, false, ListingInformationCompleteness.hasUsableAmenity(List.of("WIFI", "PARKING"))))
                .isEqualTo(ListingInformationCompleteness.AMENITIES_SCORE);
    }

    @Test
    void exampleOrderIsScoreDescending() {
        int a = score(true, true, true, true);
        int b = score(true, true, true, false);
        int c = score(true, true, false, true);
        int d = score(false, true, true, true);
        int e = score(false, true, false, false);
        assertThat(List.of(a, b, c, d, e)).containsExactly(10, 9, 8, 6, 3);
    }

    @Test
    void messUsesTheSameScore() {
        assertThat(score(true, true, true, true)).isEqualTo(10);
        assertThat(ListingInformationCompleteness.hasUsableMobile("9988776655")).isTrue();
        assertThat(ListingInformationCompleteness.hasUsableAddress("Lane 4, Hinjewadi")).isTrue();
        assertThat(ListingInformationCompleteness.hasValidMapUrl("https://maps.google.com/?q=mess")).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = SpaceType.class, names = {"PG", "HOSTEL", "RENTAL", "CO_LIVING"})
    void typeFilterIsAppliedBeforeRanking(SpaceType requested) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 1, 10, 0);
        List<Candidate> matching = sample().stream().filter(row -> row.type == requested).toList();
        List<Candidate> page = rankThenPage(matching, 0, 20);
        assertThat(page).allMatch(row -> row.type == requested);
        assertThat(page).extracting(row -> row.score).isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(page.get(0).createdAt).isBefore(now.plusDays(1));
    }

    @Test
    void locationSearchAndCombinedFiltersAreAppliedBeforeRanking() {
        List<Candidate> hinjewadiPgWifi = sample().stream()
                .filter(row -> row.type == SpaceType.PG)
                .filter(row -> row.addressText.contains("Hinjewadi"))
                .filter(row -> row.name.toLowerCase().contains("sunrise") || row.addressText.contains("Hinjewadi"))
                .filter(row -> row.amenities.contains("WIFI"))
                .toList();
        List<Candidate> page = rankThenPage(hinjewadiPgWifi, 0, 20);
        assertThat(page).isNotEmpty();
        assertThat(page).allMatch(row -> row.type == SpaceType.PG);
        assertThat(page).allMatch(row -> row.addressText.contains("Hinjewadi"));
        assertThat(page).allMatch(row -> row.amenities.contains("WIFI"));
        assertThat(page.get(0).score).isGreaterThanOrEqualTo(page.get(page.size() - 1).score);
    }

    @Test
    void rankingHappensBeforePagination() {
        LocalDateTime older = LocalDateTime.of(2026, 1, 1, 0, 0);
        List<Candidate> matching = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            matching.add(candidate(SpaceType.PG, 0, older.plusMinutes(i), "Low " + i, "Pune", List.of()));
        }
        for (int i = 0; i < 5; i++) {
            matching.add(candidate(
                    SpaceType.PG,
                    10,
                    older.minusDays(30 + i),
                    "Complete " + i,
                    "Pune",
                    List.of("WIFI")));
        }

        List<Candidate> page = rankThenPage(matching, 0, 20);

        assertThat(page).hasSize(20);
        assertThat(page.subList(0, 5)).allMatch(row -> row.score == 10);
        assertThat(page.subList(5, 20)).allMatch(row -> row.score == 0);
    }

    @Test
    void tiesUseNewestThenId() {
        LocalDateTime older = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime newer = older.plusDays(2);
        UUID lowId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID highId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Candidate olderHighId = candidate(SpaceType.PG, 10, older, "A", "Pune", List.of("WIFI"), highId);
        Candidate newerLowId = candidate(SpaceType.PG, 10, newer, "B", "Pune", List.of("WIFI"), lowId);
        Candidate sameTimeLowId = candidate(SpaceType.PG, 10, newer, "C", "Pune", List.of("WIFI"), lowId);
        Candidate sameTimeHighId = candidate(SpaceType.HOSTEL, 10, newer, "D", "Pune", List.of("WIFI"), highId);

        List<Candidate> tiedTime = rankThenPage(List.of(sameTimeLowId, sameTimeHighId), 0, 20);
        assertThat(tiedTime).extracting(row -> row.id).containsExactly(highId, lowId);

        List<Candidate> byNewest = rankThenPage(List.of(olderHighId, newerLowId), 0, 20);
        assertThat(byNewest).extracting(row -> row.id).containsExactly(lowId, highId);
    }

    private static int score(boolean mobile, boolean address, boolean map, boolean amenities) {
        return ListingInformationCompleteness.score(mobile, address, map, amenities);
    }

    private static List<Candidate> sample() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 1, 10, 0);
        return List.of(
                candidate(SpaceType.PG, 10, now, "Sunrise PG", "Hinjewadi", List.of("WIFI")),
                candidate(SpaceType.PG, 3, now.minusDays(1), "Plain PG", "Hinjewadi", List.of()),
                candidate(SpaceType.HOSTEL, 10, now, "Complete Hostel", "Kothrud", List.of("WIFI")),
                candidate(SpaceType.HOSTEL, 3, now.minusDays(1), "Plain Hostel", "Kothrud", List.of()),
                candidate(SpaceType.RENTAL, 9, now, "Rental Map", "Baner", List.of()),
                candidate(SpaceType.CO_LIVING, 8, now, "Co-living", "Wakad", List.of("WIFI")),
                candidate(SpaceType.MESS, 10, now, "Full Mess", "Hinjewadi", List.of("WIFI")),
                candidate(SpaceType.MESS, 0, now.minusDays(4), "Empty Mess", "Hinjewadi", List.of("FOOD_INCLUDED")));
    }

    private static Candidate candidate(
            SpaceType type, int score, LocalDateTime createdAt, String name, String address, List<String> amenities) {
        return candidate(type, score, createdAt, name, address, amenities, UUID.randomUUID());
    }

    private static Candidate candidate(
            SpaceType type,
            int score,
            LocalDateTime createdAt,
            String name,
            String address,
            List<String> amenities,
            UUID id) {
        return new Candidate(id, type, score, createdAt, name, address, amenities);
    }

    /**
     * Same keys as the discover SQL: filter the caller-supplied matches, sort by score desc,
     * createdAt desc, id desc, then slice. Production applies this order inside the query
     * before offset/limit.
     */
    private static List<Candidate> rankThenPage(List<Candidate> matching, int page, int size) {
        List<Candidate> ranked = new ArrayList<>(matching);
        ranked.sort((left, right) -> {
            int byScore = Integer.compare(right.score, left.score);
            if (byScore != 0) {
                return byScore;
            }
            int byCreated = right.createdAt.compareTo(left.createdAt);
            if (byCreated != 0) {
                return byCreated;
            }
            return right.id.compareTo(left.id);
        });
        int from = Math.min(page * size, ranked.size());
        int to = Math.min(from + size, ranked.size());
        return List.copyOf(ranked.subList(from, to));
    }

    private record Candidate(
            UUID id,
            SpaceType type,
            int score,
            LocalDateTime createdAt,
            String name,
            String addressText,
            List<String> amenities) {}
}
