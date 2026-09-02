package com.acomi.acomi_backend.property.api.dto.response;

import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationSource;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationStatus;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PropertyRegistrationListItemResponse {

    private UUID id;
    private String reference;
    private SpaceType propertyType;
    private String propertyName;
    private String ownerName;
    private String mobileNumber;
    private String alternateMobileNumber;
    private String city;
    private String state;
    private String pincode;
    private PropertyRegistrationStatus status;
    private PropertyRegistrationSource source;
    private LocalDateTime claimedAt;
    private LocalDateTime createdAt;
    private boolean testLead;
}
