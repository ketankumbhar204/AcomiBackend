package com.acomi.acomi_backend.location.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.location.api.dto.response.LocationRecordResponse;
import com.acomi.acomi_backend.location.domain.model.LocationErrorCodes;
import com.acomi.acomi_backend.storage.config.StorageProperties;
import com.acomi.acomi_backend.storage.infrastructure.provider.memory.InMemoryStorageProvider;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class LocationReferenceServiceTest {

    private static final String SAMPLE = """
            [
              {
                "officeName": "Yeshwantnagar S.O (Solapur)",
                "pincode": 413118,
                "taluk": "Malsras",
                "districtName": "Solapur",
                "stateName": "MAHARASHTRA"
              },
              {
                "officeName": "Example B.O",
                "pincode": 411007,
                "taluk": "Haveli",
                "districtName": "Pune",
                "stateName": "MAHARASHTRA"
              },
              {
                "officeName": "Aundh S.O",
                "pincode": 411007,
                "taluk": "Haveli",
                "districtName": "Pune",
                "stateName": "MAHARASHTRA"
              },
              {
                "officeName": "Aundh B.O",
                "pincode": 422510,
                "taluk": "Kopargaon",
                "districtName": "Ahmednagar",
                "stateName": "MAHARASHTRA"
              },
              {
                "officeName": "Example H.O",
                "pincode": 500001,
                "taluk": "Hyderabad",
                "districtName": "Hyderabad",
                "stateName": "ANDHRA PRADESH"
              },
              {
                "officeName": "Ada B.O",
                "pincode": 504293,
                "taluk": "Asifabad",
                "districtName": "Adilabad",
                "stateName": "ANDHRA PRADESH"
              },
              {
                "officeName": null,
                "pincode": null,
                "taluk": null,
                "districtName": null,
                "stateName": null
              },
              {
                "officeName": "Nameless",
                "pincode": "411045",
                "taluk": "Haveli",
                "districtName": "Pune",
                "stateName": "  MAHARASHTRA  "
              }
            ]
            """;

    private InMemoryStorageProvider storageProvider;
    private LocationReferenceService service;

    @BeforeEach
    void setUp() {
        storageProvider = spy(new InMemoryStorageProvider());
        StorageProperties properties = new StorageProperties();
        properties.setBucket("acomi-prod-files");
        putJson(SAMPLE);
        service = new LocationReferenceService(storageProvider, properties);
    }

    @Test
    void extractsUniqueStates() {
        assertThat(service.listStates()).containsExactly("ANDHRA PRADESH", "MAHARASHTRA");
    }

    @Test
    void filtersDistrictsByState() {
        assertThat(service.listDistricts("MAHARASHTRA"))
                .containsExactly("Ahmednagar", "Pune", "Solapur");
        assertThat(service.listDistricts("ANDHRA PRADESH")).containsExactly("Adilabad", "Hyderabad");
        assertThat(service.listDistricts("unknown")).isEmpty();
    }

    @Test
    void filtersTalukasByStateAndDistrict() {
        assertThat(service.listTalukas("MAHARASHTRA", "Pune")).containsExactly("Haveli");
        assertThat(service.listTalukas("MAHARASHTRA", "Solapur")).containsExactly("Malsras");
        assertThat(service.listTalukas("MAHARASHTRA", "missing")).isEmpty();
    }

    @Test
    void looksUpLocationsAndPincodes() {
        List<LocationRecordResponse> areas = service.listAreas("MAHARASHTRA", "Solapur", "Malsras");
        assertThat(areas).hasSize(1);
        LocationRecordResponse yeshwantnagar = areas.get(0);
        assertThat(yeshwantnagar.getState()).isEqualTo("MAHARASHTRA");
        assertThat(yeshwantnagar.getDistrict()).isEqualTo("Solapur");
        assertThat(yeshwantnagar.getCityTaluka()).isEqualTo("Malsras");
        assertThat(yeshwantnagar.getLocation()).isEqualTo("Yeshwantnagar");
        assertThat(yeshwantnagar.getPincode()).isEqualTo("413118");
    }

    @Test
    void keepsDuplicateLocationNamesDistinguishable() {
        List<LocationRecordResponse> matches = service.search("Aundh", 20);
        assertThat(matches).hasSize(2);
        assertThat(matches)
                .extracting(LocationRecordResponse::getPincode)
                .containsExactlyInAnyOrder("411007", "422510");
        assertThat(matches)
                .extracting(LocationRecordResponse::getDistrict)
                .containsExactlyInAnyOrder("Pune", "Ahmednagar");
    }

    @Test
    void normalizesBoAndHoInLookups() {
        assertThat(service.listAreas("MAHARASHTRA", "Pune", "Haveli"))
                .extracting(LocationRecordResponse::getLocation)
                .contains("Example", "Aundh", "Nameless");
        assertThat(service.listAreas("ANDHRA PRADESH", "Hyderabad", "Hyderabad"))
                .extracting(LocationRecordResponse::getLocation)
                .containsExactly("Example");
    }

    @Test
    void doesNotMutateOriginalOfficeNameInSourcePayload() throws Exception {
        putJson("""
                [{"officeName":"Yeshwantnagar S.O (Solapur)","pincode":413118,"taluk":"Malsras","districtName":"Solapur","stateName":"MAHARASHTRA"}]
                """);
        LocationReferenceService fresh = new LocationReferenceService(storageProvider, storageProperties());
        assertThat(fresh.listAreas("MAHARASHTRA", "Solapur", "Malsras"))
                .extracting(LocationRecordResponse::getLocation)
                .containsExactly("Yeshwantnagar");
        String stored = new String(
                storageProvider
                        .openStream("acomi-prod-files", LocationReferenceService.OBJECT_KEY)
                        .readAllBytes(),
                StandardCharsets.UTF_8);
        assertThat(stored).contains("\"officeName\":\"Yeshwantnagar S.O (Solapur)\"");
    }

    @Test
    void skipsRowsMissingStateAndAcceptsNullFields() {
        assertThat(service.search("Nameless", 10)).hasSize(1);
        assertThat(service.search("Nameless", 10).get(0).getPincode()).isEqualTo("411045");
        assertThat(service.listStates()).doesNotContain("");
    }

    @Test
    void r2ReadFailureIsControlled() {
        StorageProperties properties = new StorageProperties();
        properties.setBucket("acomi-prod-files");
        LocationReferenceService missing =
                new LocationReferenceService(new InMemoryStorageProvider(), properties);
        assertThatThrownBy(missing::listStates)
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> {
                    BusinessException ex = (BusinessException) error;
                    assertThat(ex.getErrorCode()).isEqualTo(LocationErrorCodes.LOCATION_REFERENCE_UNAVAILABLE);
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                });
    }

    @Test
    void invalidJsonIsControlled() {
        putJson("{not-json");
        LocationReferenceService broken = new LocationReferenceService(storageProvider, storageProperties());
        assertThatThrownBy(broken::listStates)
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(LocationErrorCodes.LOCATION_REFERENCE_UNAVAILABLE);
    }

    @Test
    void cachesDatasetAndDoesNotRereadForEveryRequest() {
        service.listStates();
        service.listDistricts("MAHARASHTRA");
        service.listTalukas("MAHARASHTRA", "Pune");
        service.listAreas("MAHARASHTRA", "Pune", "Haveli");
        service.search("Aundh", 20);
        assertThat(service.loadCount()).isEqualTo(1);
        verify(storageProvider, times(1))
                .openStream("acomi-prod-files", LocationReferenceService.OBJECT_KEY);
    }

    @Test
    void pincodeFromNumberIsString() {
        assertThat(LocationReferenceService.pincodeToString(413118)).isEqualTo("413118");
        assertThat(LocationReferenceService.pincodeToString(null)).isEqualTo("");
        assertThat(LocationReferenceService.pincodeToString(" 411007 ")).isEqualTo("411007");
    }

    @Test
    void searchRequiresTwoCharactersAndCapsResults() {
        assertThat(service.search("A", 20)).isEmpty();
        assertThat(service.search(null, 20)).isEmpty();
        assertThat(service.search("Au", 1)).hasSize(1);
        assertThat(service.search("Aundh", 100)).hasSizeLessThanOrEqualTo(LocationReferenceService.MAX_SEARCH_LIMIT);
        assertThat(service.search("Aundh", null)).hasSizeLessThanOrEqualTo(LocationReferenceService.DEFAULT_SEARCH_LIMIT);
    }

    @Test
    void multiKeywordAndContextRankPuneAundhFirst() {
        putJson("""
                [
                  {"officeName":"Aundh S.O","pincode":176202,"taluk":"Nurpur","districtName":"Kangra","stateName":"HIMACHAL PRADESH"},
                  {"officeName":"Aundh S.O","pincode":243501,"taluk":"Bareilly","districtName":"Bareilly","stateName":"UTTAR PRADESH"},
                  {"officeName":"Aundh S.O","pincode":411007,"taluk":"Haveli","districtName":"Pune","stateName":"MAHARASHTRA"},
                  {"officeName":"Aundh T.S. S.O","pincode":411007,"taluk":"Haveli","districtName":"Pune","stateName":"MAHARASHTRA"},
                  {"officeName":"Aundh Camp S.O","pincode":411027,"taluk":"Haveli","districtName":"Pune","stateName":"MAHARASHTRA"},
                  {"officeName":"Hinjawadi S.O","pincode":411057,"taluk":"Mulshi","districtName":"Pune","stateName":"MAHARASHTRA"}
                ]
                """);
        LocationReferenceService ranked = new LocationReferenceService(storageProvider, storageProperties());

        List<LocationRecordResponse> plain = ranked.search("aundh", 20);
        assertThat(plain).hasSizeGreaterThanOrEqualTo(3);
        assertThat(plain).extracting(LocationRecordResponse::getPincode)
                .contains("176202", "243501", "411007", "411027");

        List<LocationRecordResponse> contextual =
                ranked.search("aundh", 20, "MAHARASHTRA", "Pune", null);
        assertThat(contextual.get(0).getDistrict()).isEqualTo("Pune");
        assertThat(contextual.get(0).getState()).isEqualTo("MAHARASHTRA");
        assertThat(contextual).extracting(LocationRecordResponse::getPincode)
                .contains("176202", "243501");

        assertThat(ranked.search("aundh pune", 20).get(0).getDistrict()).isEqualTo("Pune");
        assertThat(ranked.search("pune aundh", 20).get(0).getDistrict()).isEqualTo("Pune");
        assertThat(ranked.search("aundh, pune", 20).get(0).getDistrict()).isEqualTo("Pune");
        assertThat(ranked.search("411007 aundh", 20).get(0).getPincode()).isEqualTo("411007");
        assertThat(ranked.search("hinjewadi", 20).get(0).getLocation()).isEqualTo("Hinjawadi");
    }

    private void putJson(String json) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        storageProvider.putStream(
                "acomi-prod-files",
                LocationReferenceService.OBJECT_KEY,
                new ByteArrayInputStream(bytes),
                bytes.length,
                "application/json");
    }

    private static StorageProperties storageProperties() {
        StorageProperties properties = new StorageProperties();
        properties.setBucket("acomi-prod-files");
        return properties;
    }
}
