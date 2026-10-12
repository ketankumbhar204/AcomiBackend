package com.acomi.acomi_backend.meal.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.meal.api.dto.request.DailyMenuOptionRequest;
import com.acomi.acomi_backend.meal.api.dto.response.DailyMenuOptionResponse;
import com.acomi.acomi_backend.meal.api.dto.response.MealPollOptionResponse;
import com.acomi.acomi_backend.meal.domain.model.DailyMenuEntryType;
import com.acomi.acomi_backend.meal.domain.model.MealPollOptionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class MealExtraJsonMappingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void requestReadsIsExtraTrueFromClientPayload() throws Exception {
        DailyMenuOptionRequest request = mapper.readValue(
                """
                {"label":"Papad","entryType":"PACKAGE","isExtra":true,"isAvailable":true,"sortOrder":1}
                """,
                DailyMenuOptionRequest.class);

        assertThat(request.isExtra()).isTrue();
        assertThat(request.isAvailable()).isTrue();
    }

    @Test
    void requestReadsIsAvailableFalse() throws Exception {
        DailyMenuOptionRequest request = mapper.readValue(
                """
                {"label":"Thali","entryType":"COMBO","isExtra":false,"isAvailable":false,"sortOrder":1}
                """,
                DailyMenuOptionRequest.class);

        assertThat(request.isExtra()).isFalse();
        assertThat(request.isAvailable()).isFalse();
    }

    @Test
    void menuOptionResponseWritesIsExtra() throws Exception {
        DailyMenuOptionResponse response = DailyMenuOptionResponse.builder()
                .label("Papad")
                .entryType(DailyMenuEntryType.PACKAGE)
                .sortOrder(1)
                .available(true)
                .extra(true)
                .build();

        String json = mapper.writeValueAsString(response);
        assertThat(json).contains("\"isExtra\":true");
    }

    @Test
    void pollOptionResponseWritesIsExtra() throws Exception {
        MealPollOptionResponse response = MealPollOptionResponse.builder()
                .optionType(MealPollOptionType.MENU_ENTRY)
                .sortOrder(1)
                .label("Papad")
                .extra(true)
                .build();

        String json = mapper.writeValueAsString(response);
        assertThat(json).contains("\"isExtra\":true");
    }
}
