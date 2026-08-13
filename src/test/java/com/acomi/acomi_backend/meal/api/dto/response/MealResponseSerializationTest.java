package com.acomi.acomi_backend.meal.api.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.meal.domain.model.FoodScope;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MealResponseSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void foodCategoryResponse_usesIsActiveJsonName() throws Exception {
        FoodCategoryResponse response = FoodCategoryResponse.builder()
                .categoryId(UUID.fromString("11111111-1111-1111-1111-111111110001"))
                .name("Breads")
                .sortOrder(1)
                .scope(FoodScope.GLOBAL)
                .active(true)
                .itemCount(8)
                .build();

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"isActive\":true");
        assertThat(json).doesNotContain("\"active\":");
    }

    @Test
    void foodItemResponse_usesIsCustomAndIsActiveJsonNames() throws Exception {
        FoodItemResponse response = FoodItemResponse.builder()
                .itemId(UUID.randomUUID())
                .categoryId(UUID.randomUUID())
                .categoryName("Breads")
                .name("Chapati")
                .scope(FoodScope.GLOBAL)
                .custom(false)
                .active(true)
                .build();

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"isActive\":true");
        assertThat(json).contains("\"isCustom\":false");
        assertThat(json).doesNotContain("\"active\":");
        assertThat(json).doesNotContain("\"custom\":");
    }
}
