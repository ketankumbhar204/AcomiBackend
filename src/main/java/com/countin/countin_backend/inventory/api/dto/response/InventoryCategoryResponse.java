package com.countin.countin_backend.inventory.api.dto.response;

import com.countin.countin_backend.inventory.infrastructure.persistence.entity.InventoryCategoryEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InventoryCategoryResponse {

    private UUID categoryId;
    private UUID spaceId;
    private String name;
    private String code;
    private String iconKey;
    private int sortOrder;

    @JsonProperty("isSystem")
    private boolean system;

    @JsonProperty("isDefault")
    private boolean isDefault;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static InventoryCategoryResponse from(InventoryCategoryEntity entity) {
        return InventoryCategoryResponse.builder()
                .categoryId(entity.getId())
                .spaceId(entity.getSpace().getId())
                .name(entity.getName())
                .code(entity.getCode())
                .iconKey(entity.getIconKey())
                .sortOrder(entity.getSortOrder())
                .system(entity.isDefault())
                .isDefault(entity.isDefault())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
