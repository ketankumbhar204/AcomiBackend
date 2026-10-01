package com.acomi.acomi_backend.location.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.location.api.dto.response.LocationRecordResponse;
import com.acomi.acomi_backend.location.application.service.LocationReferenceService;
import com.acomi.acomi_backend.storage.config.StorageProperties;
import com.acomi.acomi_backend.storage.infrastructure.provider.memory.InMemoryStorageProvider;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocationAutocompleteMatcherTest {

    private static final String SAMPLE = """
            [
              {"officeName":"Aundh S.O","pincode":411007,"taluk":"Haveli","districtName":"Pune","stateName":"MAHARASHTRA"},
              {"officeName":"Aundh B.O","pincode":422510,"taluk":"Kopargaon","districtName":"Ahmednagar","stateName":"MAHARASHTRA"},
              {"officeName":"Hinjewadi S.O","pincode":411057,"taluk":"Mulshi","districtName":"Pune","stateName":"MAHARASHTRA"}
            ]
            """;

    private LocationReferenceService locations;
    private LocationAutocompleteMatcher matcher;

    @BeforeEach
    void setUp() {
        InMemoryStorageProvider storage = new InMemoryStorageProvider();
        StorageProperties properties = new StorageProperties();
        properties.setBucket("acomi-files");
        byte[] bytes = SAMPLE.getBytes(StandardCharsets.UTF_8);
        storage.putStream(
                "acomi-files",
                LocationReferenceService.OBJECT_KEY,
                new ByteArrayInputStream(bytes),
                bytes.length,
                "application/json");
        locations = new LocationReferenceService(storage, properties);
        matcher = new LocationAutocompleteMatcher(locations);
    }

    @Test
    void matchesPincodeWithoutMergingAnotherAundh() {
        LocationRecordResponse match = matcher.match("Aundh", "Pune", "Pune", "Maharashtra", "411007", null, null);

        assertThat(match).isNotNull();
        assertThat(match.getPincode()).isEqualTo("411007");
        assertThat(match.getDistrict()).isEqualTo("Pune");
        assertThat(match.getState()).isEqualTo("MAHARASHTRA");
        assertThat(match.getCityTaluka()).isEqualTo("Haveli");
    }

    @Test
    void matchesKnownSpellingAliasAndDistrictSuffix() {
        LocationRecordResponse match =
                matcher.match("Hinjawadi", "Pune", "Pune District", "Maharashtra", "411057", null, null);

        assertThat(match).isNotNull();
        assertThat(match.getPincode()).isEqualTo("411057");
        assertThat(match.getLocation()).isEqualTo("Hinjewadi");
    }

    @Test
    void doesNotGuessWhenPincodesDiffer() {
        LocationRecordResponse match = matcher.match("Aundh", null, null, "Maharashtra", null, "MAHARASHTRA", "Pune");

        assertThat(match).isNull();
    }

    @Test
    void doesNotCreateALocationWhenNothingMatches() {
        List<String> before = locations.listStates();
        LocationRecordResponse match = matcher.match("Nowhere", "Atlantis", null, "Maharashtra", null, null, null);

        assertThat(match).isNull();
        assertThat(locations.listStates()).isEqualTo(before);
        assertThat(locations.listAreas("MAHARASHTRA", "Pune", "Mulshi")).hasSize(1);
    }
}
