package com.acomi.acomi_backend.admin.api.dto.response;

import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminRegistrationConvertResponse {

    private UUID registrationId;
    private String reference;
    private UUID spaceId;
    private String spaceName;
    private boolean discoverable;
}
