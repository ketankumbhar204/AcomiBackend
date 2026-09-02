package com.acomi.acomi_backend.mess.api.dto.response;

import com.acomi.acomi_backend.mess.domain.model.MessRegistrationSource;
import com.acomi.acomi_backend.mess.domain.model.MessRegistrationStatus;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MessRegistrationListItemResponse {

    private UUID id;
    private String reference;
    private String messName;
    private String ownerName;
    private String mobileNumber;
    private String alternateMobileNumber;
    private String city;
    private String state;
    private String pincode;
    private MessRegistrationStatus status;
    private MessRegistrationSource source;
    private LocalDateTime claimedAt;
    private LocalDateTime createdAt;
    private boolean testLead;
}
