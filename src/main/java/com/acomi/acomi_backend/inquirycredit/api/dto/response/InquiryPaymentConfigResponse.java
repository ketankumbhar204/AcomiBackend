package com.acomi.acomi_backend.inquirycredit.api.dto.response;

import com.acomi.acomi_backend.inquirycredit.domain.model.AndroidInquiryBillingMode;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InquiryPaymentConfigResponse {

    private UUID configId;
    private boolean enabled;
    private String upiId;
    private UUID qrFileId;
    private String qrUrl;
    private String whatsappNumber;
    private String instructions;

    /** Free WEB (email) enquiries per day before paid credits. */
    private Integer webFreeDailyLimit;

    /** ANDROID billing mode: FREE or CREDITS. */
    private AndroidInquiryBillingMode androidBillingMode;

    /** Free ANDROID enquiries per day when mode is CREDITS. */
    private Integer androidFreeDailyLimit;

    /** ANDROID hourly rate limit. */
    private Integer androidHourlyRateLimit;

    private List<InquiryCreditPackageResponse> packages;
}
