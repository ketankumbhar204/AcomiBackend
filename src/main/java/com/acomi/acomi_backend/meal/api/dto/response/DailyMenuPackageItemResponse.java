package com.acomi.acomi_backend.meal.api.dto.response;

import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DailyMenuPackageItemResponse {

    private UUID itemId;
    private String name;
}
