package com.acomi.acomi_backend.enquiry.api.dto.request;

import com.acomi.acomi_backend.enquiry.domain.model.EnquiryDeliveryChannel;
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

    /**
     * Optional Public Website / client delivery intent.
     * {@code APP} records an ACOMI App contact delivery (independent of EMAIL).
     * {@code EMAIL} creates/reuses the enquiry only — email send is via email-contact.
     * When null: ANDROID client header implies APP; WEB create does not record a delivery.
     */
    private EnquiryDeliveryChannel deliveryChannel;
}
