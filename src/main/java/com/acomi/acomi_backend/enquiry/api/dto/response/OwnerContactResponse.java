package com.acomi.acomi_backend.enquiry.api.dto.response;

import lombok.Builder;
import lombok.Getter;

/** Owner contact. Admin always; members only for ANDROID SHARED enquiries via SpaceEnquiryResponse. */
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
