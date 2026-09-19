package com.acomi.acomi_backend.inquirycredit.api.dto.response;

import lombok.Builder;
import lombok.Getter;

/** Web enquiry free-quota snapshot for the authenticated seeker. */
@Getter
@Builder
public class InquiryQuotaResponse {

    /** Max free WEB enquiries allowed per calendar day. */
    private int dailyFreeLimit;

    /** Free WEB enquiries already used today. */
    private int freeUsedToday;

    /** Free WEB enquiries remaining today (never negative). */
    private int freeRemainingToday;

    /** Paid inquiry credits currently available. */
    private int availableCredits;
}
