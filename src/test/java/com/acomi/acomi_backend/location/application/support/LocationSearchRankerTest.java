package com.acomi.acomi_backend.location.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.location.domain.model.LocationRecord;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocationSearchRankerTest {

    @Test
    void splitsCommaAndWhitespaceKeywords() {
        assertThat(LocationSearchRanker.keywords("aundh pune")).containsExactly("aundh", "pune");
        assertThat(LocationSearchRanker.keywords("pune aundh")).containsExactly("pune", "aundh");
        assertThat(LocationSearchRanker.keywords("aundh, pune")).containsExactly("aundh", "pune");
        assertThat(LocationSearchRanker.keywords("411007 aundh")).containsExactly("411007", "aundh");
    }

    @Test
    void rejectsEmptyShortAndNullQueries() {
        assertThat(LocationSearchRanker.keywords(null)).isEmpty();
        assertThat(LocationSearchRanker.keywords("")).isEmpty();
        assertThat(LocationSearchRanker.keywords("A")).isEmpty();
        assertThat(LocationSearchRanker.keywords("  ,  ")).isEmpty();
    }

    @Test
    void contextBoostsPuneWithoutChangingIdentity() {
        LocationRecord pune = new LocationRecord("Aundh S.O", "Aundh", "Haveli", "Pune", "MAHARASHTRA", "411007");
        LocationRecord kangra = new LocationRecord("Aundh S.O", "Aundh", "Nurpur", "Kangra", "HIMACHAL PRADESH", "176202");
        List<String> keywords = LocationSearchRanker.keywords("aundh");
        int withContext = LocationSearchRanker.score(pune, keywords, "MAHARASHTRA", "Pune", null);
        int withoutContext = LocationSearchRanker.score(kangra, keywords, "MAHARASHTRA", "Pune", null);
        assertThat(withContext).isGreaterThan(withoutContext);
        assertThat(withoutContext).isGreaterThanOrEqualTo(0);
        assertThat(LocationSearchRanker.identity(pune)).isNotEqualTo(LocationSearchRanker.identity(kangra));
    }
}
