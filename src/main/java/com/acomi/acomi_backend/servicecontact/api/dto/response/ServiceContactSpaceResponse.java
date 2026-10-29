package com.acomi.acomi_backend.servicecontact.api.dto.response;

import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ServiceContactSpaceResponse {

    private final UUID spaceId;
    private final String name;
    private final String type;
}
