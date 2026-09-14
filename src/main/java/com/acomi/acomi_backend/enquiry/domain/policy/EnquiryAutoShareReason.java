package com.acomi.acomi_backend.enquiry.domain.policy;

/**
 * Internal auto-share decision reasons. Not returned to customers.
 */
public enum EnquiryAutoShareReason {
    ALLOWED,
    AUTO_SHARE_DISABLED,
    SPACE_INACTIVE,
    SPACE_NOT_DISCOVERABLE,
    TEST_LISTING,
    OWNER_NOT_LINKED,
    OWNER_INACTIVE,
    OWNER_CONTACT_UNAVAILABLE,
    REQUESTER_EMAIL_UNAVAILABLE
}
