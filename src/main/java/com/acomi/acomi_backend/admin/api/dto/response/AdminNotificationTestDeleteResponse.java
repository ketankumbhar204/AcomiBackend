package com.acomi.acomi_backend.admin.api.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdminNotificationTestDeleteResponse {
    int deletedCount;
}
