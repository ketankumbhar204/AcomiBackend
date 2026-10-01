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

    /** ANDROID billing: FREE or CREDITS. */
    private String androidBillingMode;

    /** Free ANDROID enquiries per day when billing mode is CREDITS. */
    private Integer androidFreeDailyLimit;

    /** ANDROID hourly rate limit (abuse protection). */
    private Integer androidHourlyRateLimit;
}
