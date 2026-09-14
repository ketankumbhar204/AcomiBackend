package com.acomi.acomi_backend.admin.domain.model;

/**
 * Admin Recent Activity event types reconstructable from existing tables (V1).
 */
public enum AdminActivityType {
    NEW_ENQUIRY,
    NEW_PROPERTY_REGISTRATION,
    NEW_MESS_REGISTRATION,
    NEW_USER_REGISTRATION,
    NEW_PROPERTY_LISTED,
    NEW_MESS_LISTED,
    LEAD_CLAIMED_PROPERTY,
    LEAD_CLAIMED_MESS,
    ENQUIRY_SHARED,
    ENQUIRY_REJECTED,
    ADDRESS_SAVED
}
