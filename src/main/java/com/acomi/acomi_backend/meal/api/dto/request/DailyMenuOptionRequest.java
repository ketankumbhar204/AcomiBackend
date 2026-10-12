package com.acomi.acomi_backend.meal.api.dto.request;

import com.acomi.acomi_backend.meal.domain.model.DailyMenuEntryType;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DailyMenuOptionRequest {

    /** Existing entry id from GET response; omit for new options. */
    private UUID optionId;

    private DailyMenuEntryType entryType;

    private UUID comboId;

    private UUID itemId;

    /** Required for PACKAGE entries — items included in this meal-only package. */
    private List<UUID> itemIds;

    @NotBlank
    private String label;

    private BigDecimal price;

    private String currencyCode;

    private int sortOrder;

    /**
     * Field names must not start with {@code is}. Lombok then exposes {@code available}
     * / {@code extra}, and Jackson drops the client's {@code isAvailable} / {@code isExtra}.
     */
    @JsonProperty("isAvailable")
    private boolean available = true;

    /** Mess-only add-on flag; ignored / rejected for non-Mess spaces. */
    @JsonProperty("isExtra")
    private boolean extra = false;
}
