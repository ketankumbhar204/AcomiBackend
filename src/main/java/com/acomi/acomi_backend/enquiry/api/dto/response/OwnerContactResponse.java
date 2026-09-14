package com.acomi.acomi_backend.enquiry.api.dto.response;

import lombok.Builder;
import lombok.Getter;

/** Admin-only owner contact. Never returned from member enquiry or discovery APIs. */
@Getter
@Builder
public class OwnerContactResponse {

    private String ownerName;
    private String mobileNumber;
    private String alternateMobileNumber;
    private String additionalMobileNumber;
    private String email;
    private boolean available;
}
