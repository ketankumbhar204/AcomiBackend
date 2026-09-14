package com.acomi.acomi_backend.admin.api.dto.response;

import com.acomi.acomi_backend.admin.domain.model.AdminActivityTargetType;
import com.acomi.acomi_backend.admin.domain.model.AdminActivityType;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminActivityItemResponse {

    /** Stable id for the activity row: "{type}:{targetId}:{timestampEpoch}" */
    private String id;

    private AdminActivityType type;
    private String title;
    private String description;
    private LocalDateTime timestamp;
    private AdminActivityTargetType targetType;
    private UUID targetId;
    private AdminActivityTargetType secondaryTargetType;
    private UUID secondaryTargetId;
}
