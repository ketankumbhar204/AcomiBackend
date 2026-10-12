package com.acomi.acomi_backend.inquirycredit.api.dto.request;

import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdatePaymentConfigRequest {

    private String upiId;
    private UUID qrFileId;
    private String whatsappNumber;
    private String instructions;
    private Boolean enabled;

    /** Free WEB (email) enquiries per calendar day. */
    private Integer webFreeDailyLimit;

    /**
     * Website instant contact views allowed per day. Independent of
     * {@link #webFreeDailyLimit}. 0 disables the option.
     */
    private Integer viewHereDailyLimit;

    /** Website instant contact viewing. Null leaves the stored value unchanged. */
    private Boolean webViewHereEnabled;

    /** Mobile instant contact viewing. Null leaves the stored value unchanged. */
    private Boolean androidViewHereEnabled;

    /** Mobile instant contact views per day. Independent of mobile enquiry credits. */
    private Integer androidViewHereDailyLimit;

    /** ANDROID billing: FREE or CREDITS. */
    private String androidBillingMode;

    /** Free ANDROID enquiries per day when billing mode is CREDITS. */
    private Integer androidFreeDailyLimit;

    /** ANDROID hourly rate limit (abuse protection). */
    private Integer androidHourlyRateLimit;
}
