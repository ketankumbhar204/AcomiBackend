package com.countin.countin_backend.meal.application.support;

import com.countin.countin_backend.meal.api.dto.response.DailyMenuOptionResponse;
import com.countin.countin_backend.meal.api.dto.response.DailyMenuPackageItemResponse;
import com.countin.countin_backend.meal.domain.model.DailyMenuEntryType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** JSON-serializable last-shared menu payload for MODIFIED menus. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class PublishedMenuSnapshot {

    private String notes;
    private List<SnapshotOption> options;

    public static PublishedMenuSnapshot from(
            String notes, List<DailyMenuOptionResponse> optionResponses) {
        List<SnapshotOption> options = new ArrayList<>();
        if (optionResponses != null) {
            for (DailyMenuOptionResponse option : optionResponses) {
                options.add(SnapshotOption.from(option));
            }
        }
        return PublishedMenuSnapshot.builder().notes(notes).options(options).build();
    }

    public List<DailyMenuOptionResponse> toOptionResponses() {
        if (options == null || options.isEmpty()) {
            return List.of();
        }
        return options.stream().map(SnapshotOption::toResponse).toList();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SnapshotOption {
        private UUID optionId;
        private DailyMenuEntryType entryType;
        private UUID comboId;
        private UUID itemId;
        private String label;
        private int sortOrder;
        private BigDecimal price;
        private String currencyCode;
        private boolean available;
        @com.fasterxml.jackson.annotation.JsonProperty("isExtra")
        private boolean extra;
        private List<SnapshotPackageItem> packageItems;

        static SnapshotOption from(DailyMenuOptionResponse option) {
            List<SnapshotPackageItem> items = null;
            if (option.getPackageItems() != null) {
                items = option.getPackageItems().stream()
                        .map(pi -> SnapshotPackageItem.builder()
                                .itemId(pi.getItemId())
                                .name(pi.getName())
                                .build())
                        .toList();
            }
            return SnapshotOption.builder()
                    .optionId(option.getOptionId())
                    .entryType(option.getEntryType())
                    .comboId(option.getComboId())
                    .itemId(option.getItemId())
                    .label(option.getLabel())
                    .sortOrder(option.getSortOrder())
                    .price(option.getPrice())
                    .currencyCode(option.getCurrencyCode())
                    .available(option.isAvailable())
                    .extra(option.isExtra())
                    .packageItems(items)
                    .build();
        }

        DailyMenuOptionResponse toResponse() {
            List<DailyMenuPackageItemResponse> items = null;
            if (packageItems != null && !packageItems.isEmpty()) {
                items = packageItems.stream()
                        .map(pi -> DailyMenuPackageItemResponse.builder()
                                .itemId(pi.getItemId())
                                .name(pi.getName())
                                .build())
                        .toList();
            }
            return DailyMenuOptionResponse.builder()
                    .optionId(optionId)
                    .entryType(entryType)
                    .comboId(comboId)
                    .itemId(itemId)
                    .label(label)
                    .sortOrder(sortOrder)
                    .price(price)
                    .currencyCode(currencyCode)
                    .available(available)
                    .extra(extra)
                    .packageItems(items)
                    .build();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SnapshotPackageItem {
        private UUID itemId;
        private String name;
    }
}
