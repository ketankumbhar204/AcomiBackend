package com.acomi.acomi_backend.enquiry.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateSpaceEnquiryRequest {

    /**
     * Required when the authenticated account has no email.
     * Stored on the enquiry and remembered in {@code users.enquiry_emails}.
     * Fills {@code users.email} only when that profile field is still blank.
     */
    @Email
    @Size(max = 255)
    private String email;
}
