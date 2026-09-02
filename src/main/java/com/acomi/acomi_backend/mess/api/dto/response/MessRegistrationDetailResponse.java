package com.acomi.acomi_backend.mess.api.dto.response;

import com.acomi.acomi_backend.mess.domain.model.MessRegistrationSource;
import com.acomi.acomi_backend.mess.domain.model.MessRegistrationStatus;
import com.acomi.acomi_backend.registration.domain.model.RegistrationClaimVia;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MessRegistrationDetailResponse {

    private UUID id;
    private String reference;
    private String messName;
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
    private BigDecimal monthlyPrice;
    private BigDecimal mealPrice;
    private Integer capacityEstimate;
    private MessRegistrationStatus status;
    private MessRegistrationSource source;
    private UUID convertedSpaceId;
    private LocalDateTime claimedAt;
    private RegistrationClaimVia claimedVia;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean testLead;
}
