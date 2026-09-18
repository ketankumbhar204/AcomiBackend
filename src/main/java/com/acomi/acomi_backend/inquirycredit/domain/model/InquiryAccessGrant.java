package com.acomi.acomi_backend.inquirycredit.domain.model;

/**
 * Result of the access authorization check before a new enquiry is created.
 * The caller must invoke consumeAfterSuccessfulCreate() after the enquiry is persisted.
 */
public enum InquiryAccessGrant {
    /** Web channel — within the 5 free daily enquiries. */
    FREE_WEB,

    /** Web channel — free quota exhausted, debit 1 credit from wallet. */
    PAID_CREDIT,

    /** Android channel — no credit charge; access is always granted (rate-limited separately). */
    ANDROID_FREE
}
