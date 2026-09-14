package com.acomi.acomi_backend.enquiry.domain.policy;

import com.acomi.acomi_backend.enquiry.api.dto.response.OwnerContactResponse;

public record EnquiryAutoShareDecision(EnquiryAutoShareReason reason, OwnerContactResponse contact) {

    public boolean isAllowed() {
        return reason == EnquiryAutoShareReason.ALLOWED;
    }

    public static EnquiryAutoShareDecision allow(OwnerContactResponse contact) {
        return new EnquiryAutoShareDecision(EnquiryAutoShareReason.ALLOWED, contact);
    }

    public static EnquiryAutoShareDecision deny(EnquiryAutoShareReason reason) {
        return new EnquiryAutoShareDecision(reason, null);
    }
}
