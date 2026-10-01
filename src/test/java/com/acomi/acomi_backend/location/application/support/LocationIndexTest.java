package com.acomi.acomi_backend.location.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.location.domain.model.LocationRecord;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocationIndexTest {

    private LocationIndex index;

    @BeforeEach
    void setUp() {
        index = LocationIndex.build(List.of(
                record("Aundh S.O", "Aundh", "Haveli", "Pune", "MAHARASHTRA", "411007"),
                record("Aundh T.S. S.O", "Aundh T.S.", "Haveli", "Pune", "MAHARASHTRA", "411007"),
                record("Aundh Camp S.O", "Aundh Camp", "Haveli", "Pune", "MAHARASHTRA", "411027"),
                record("Aundh B.O", "Aundh", "Kopargaon", "Ahmednagar", "MAHARASHTRA", "422510"),
                record("Aundh S.O", "Aundh", "Nurpur", "Kangra", "HIMACHAL PRADESH", "176202"),
                record("Aundh S.O", "Aundh", "Bareilly", "Bareilly", "UTTAR PRADESH", "243501"),
                record("Hinjawadi S.O", "Hinjawadi", "Mulshi", "Pune", "MAHARASHTRA", "411057"),
                record("Baner S.O", "Baner", "Haveli", "Pune", "MAHARASHTRA", "411045"),
                record("Aundh S.O", "Aundh", "Haveli", "Pune", "MAHARASHTRA", "411007")));
    }

    @Test
    void aundhWithoutContextReturnsEveryLegitimateAundh() {
        List<LocationRecord> matches = index.search("aundh", 20);
        assertThat(matches)
                .extracting(LocationRecord::pincode)
                .contains("411007", "411027", "422510", "176202", "243501");
        assertThat(matches)
                .extracting(LocationRecord::district)
                .contains("Pune", "Ahmednagar", "Kangra", "Bareilly");
    }

    @Test
    void puneContextRanksPuneAundhFirstWithoutDroppingOthers() {
        List<LocationRecord> matches = index.search("aundh", 20, "MAHARASHTRA", "Pune", null);
        assertThat(matches.get(0).district()).isEqualTo("Pune");
        assertThat(matches.get(0).pincode()).isEqualTo("411007");
        assertThat(matches.get(0).state()).isEqualTo("MAHARASHTRA");
        assertThat(matches)
                .extracting(LocationRecord::pincode)
                .contains("176202", "243501", "422510");
    }

    @Test
    void multiKeywordAundhPuneRanksPuneFirst() {
        assertPuneAundhFirst(index.search("aundh pune", 20));
        assertPuneAundhFirst(index.search("pune aundh", 20));
        assertPuneAundhFirst(index.search("aundh, pune", 20));
    }

    @Test
    void pincodeAndNameQueryRanksMatchingPostalLocation() {
        List<LocationRecord> matches = index.search("411007 aundh", 20);
        assertThat(matches).isNotEmpty();
        assertThat(matches.get(0).pincode()).isEqualTo("411007");
        assertThat(matches.get(0).location()).startsWith("Aundh");
        assertThat(matches).allMatch(record -> "411007".equals(record.pincode()));
    }

    @Test
    void aundhTsAndAundhCampRemainDistinct() {
        List<LocationRecord> matches = index.search("aundh", 20);
        assertThat(matches)
                .anyMatch(record -> "Aundh Camp".equals(record.location()) && "411027".equals(record.pincode()));
        assertThat(matches)
                .anyMatch(record -> "411007".equals(record.pincode()) && record.location().startsWith("Aundh"));
        assertThat(matches.stream().map(LocationSearchRanker::identity).distinct().count())
                .isEqualTo(matches.size());
    }

    @Test
    void sameNormalizedNameDifferentPincodesAreNotMerged() {
        List<LocationRecord> matches = index.search("aundh", 20, "MAHARASHTRA", "Pune", null);
        assertThat(matches)
                .filteredOn(record -> "Pune".equals(record.district()))
                .extracting(LocationRecord::pincode)
                .contains("411007", "411027");
    }

    @Test
    void hinjewadiAliasMatchesHinjawadiRecord() {
        List<LocationRecord> matches = index.search("hinjewadi", 20);
        assertThat(matches)
                .anyMatch(record -> "Hinjawadi".equals(record.location()) && "411057".equals(record.pincode()));
        List<LocationRecord> withPune = index.search("hinjewadi pune", 20);
        assertThat(withPune.get(0).location()).isEqualTo("Hinjawadi");
        assertThat(withPune.get(0).district()).isEqualTo("Pune");
    }

    @Test
    void emptyOrShortQueriesStayEmpty() {
        assertThat(index.search(null, 20)).isEmpty();
        assertThat(index.search(" ", 20)).isEmpty();
        assertThat(index.search("A", 20)).isEmpty();
        assertThat(index.search("aundh", 0)).isEmpty();
    }

    @Test
    void resultsAreDeterministicAndDeduped() {
        List<LocationRecord> first = index.search("aundh", 20, "MAHARASHTRA", "Pune", null);
        List<LocationRecord> second = index.search("aundh", 20, "MAHARASHTRA", "Pune", null);
        assertThat(first).containsExactlyElementsOf(second);
        assertThat(first.stream().map(LocationSearchRanker::identity).distinct().count())
                .isEqualTo(first.size());
    }

    @Test
    void existingLimitBehaviorCapsResults() {
        assertThat(index.search("aundh", 2)).hasSize(2);
        assertThat(index.search("aundh", 50)).hasSizeLessThanOrEqualTo(50);
    }

    private static void assertPuneAundhFirst(List<LocationRecord> matches) {
        assertThat(matches).isNotEmpty();
        assertThat(matches.get(0).district()).isEqualTo("Pune");
        assertThat(matches.get(0).state()).isEqualTo("MAHARASHTRA");
        assertThat(matches.get(0).location().toLowerCase()).contains("aundh");
        assertThat(matches).noneMatch(record -> "Kangra".equals(record.district()));
        assertThat(matches).noneMatch(record -> "Bareilly".equals(record.district()));
    }

    private static LocationRecord record(
            String officeName, String location, String taluk, String district, String state, String pincode) {
        return new LocationRecord(officeName, location, taluk, district, state, pincode);
    }
}
