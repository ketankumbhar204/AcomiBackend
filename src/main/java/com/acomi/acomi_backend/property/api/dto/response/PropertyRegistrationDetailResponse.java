package com.acomi.acomi_backend.property.api.dto.response;

import com.acomi.acomi_backend.property.domain.model.PriceBasis;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationSource;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationStatus;
import com.acomi.acomi_backend.registration.domain.model.RegistrationClaimVia;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PropertyRegistrationDetailResponse {

    private UUID id;
    private String reference;
    private SpaceType propertyType;
    private String propertyName;
    private String ownerName;
    private String mobileNumber;
    private String alternateMobileNumber;
    private LocalDateTime mobileVerifiedAt;
    private String description;
    private String addressLine;
    private String city;
    private String state;
    private String pincode;
    private String mapUrl;
    private BigDecimal startingPrice;
    private PriceBasis priceBasis;
    private Integer capacityEstimate;
    private PropertyRegistrationStatus status;
    private PropertyRegistrationSource source;
    private UUID convertedSpaceId;
    private LocalDateTime claimedAt;
    private RegistrationClaimVia claimedVia;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean testLead;
    private List<PropertyRegistrationAmenityResponse> amenities;

    @Getter
    @Builder
    public static class PropertyRegistrationAmenityResponse {
        private String code;
        private String customLabel;
        private int displayOrder;
    }
}
