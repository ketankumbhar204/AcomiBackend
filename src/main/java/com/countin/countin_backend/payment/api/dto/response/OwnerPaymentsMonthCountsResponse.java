package com.countin.countin_backend.payment.api.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OwnerPaymentsMonthCountsResponse {

    private int pendingReview;
    private int submitted;
    private int changesRequested;
    private int paid;
    private int rejected;
    private int history;
    private int pendingMembers;
}
