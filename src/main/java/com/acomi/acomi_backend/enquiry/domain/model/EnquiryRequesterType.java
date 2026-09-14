package com.acomi.acomi_backend.enquiry.domain.model;

/**
 * Snapshot of the requester's relationship to ACOMI at submit time.
 * OWNER means they operate at least one Space — not that they own the listing
 * being enquired about. Self-enquiries are blocked separately.
 */
public enum EnquiryRequesterType {
    MEMBER,
    OWNER
}
