package com.acomi.acomi_backend.common.reference;

import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BusinessReferenceLookupResponse {
    private String referenceCode;
    private String entityType;
    private UUID entityId;
    private UUID spaceId;
}
