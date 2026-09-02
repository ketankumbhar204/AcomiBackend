package com.acomi.acomi_backend.address.api.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SavedAddressResponse {

    private UUID id;
    private String addressLine;
    private String city;
    private String state;
    private String pincode;
    private String mapUrl;
    private int usageCount;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createdAt;
}
