package com.acomi.acomi_backend.inquirycredit.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

/** Channel-aware enquiry quota snapshot for the authenticated seeker. */
@Getter
@Builder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InquiryQuotaResponse {

    /** Client channel this quota applies to (WEB or ANDROID). */
    private String channel;

    /** Max free enquiries allowed per calendar day for this channel. */
    private int dailyFreeLimit;

    /** Free enquiries already used today. */
    private int freeUsedToday;

    /** Free enquiries remaining today (never negative). */
    private int freeRemainingToday;

    /** Paid inquiry credits currently available. */
    private int availableCredits;

    /**
     * ANDROID only: FREE = purchases not required; CREDITS = paid after free quota.
     * Null for WEB.
     */
    private String androidBillingMode;

    /** Admin "Enable credit purchases". When false, this channel has no daily cap. */
    private Boolean purchasesEnabled;

    /** True when this channel has no daily enquiry cap (toggle off or limit 0). */
    private Boolean unlimited;
}
