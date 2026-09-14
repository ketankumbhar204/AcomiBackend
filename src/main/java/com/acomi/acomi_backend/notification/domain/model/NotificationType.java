package com.acomi.acomi_backend.notification.domain.model;

/**
 * Canonical notification / pending-action types.
 * Actionable types are used by the Action Center; informational types power the activity feed.
 */
public enum NotificationType {
    // Payments — actionable
    PAYMENT_NEEDS_REVIEW,
    PAYMENT_NEEDS_UPDATE,
    PAYMENT_OVERDUE,

    // Payments — informational
    PAYMENT_SUBMITTED,
    PAYMENT_APPROVED,
    PAYMENT_REJECTED,
    PAYMENT_UPDATE_REQUESTED,
    PAYMENT_REMINDER_SENT,

    // Meals — actionable
    MEAL_POLL_NOT_PUBLISHED,
    MEAL_RESPONSES_BELOW_THRESHOLD,
    MENU_NOT_PLANNED,
    MENU_DRAFT_PENDING_PUBLISH,
    SUBSCRIPTION_ACTIVATION_PENDING,

    // Meals — informational
    MENU_PUBLISHED,
    MEAL_POLL_PUBLISHED,
    MEAL_POLL_REMINDER,
    SUBSCRIPTION_ACTIVATION_APPROVED,
    SUBSCRIPTION_ACTIVATION_REJECTED,
    MEAL_BALANCE_UPDATED,
    MEAL_PARTICIPATION_CHANGED,

    // Accommodation — actionable
    RESERVATION_STARTING_TODAY,
    MOVE_IN_SCHEDULED_TODAY,
    MOVE_OUT_SCHEDULED_TODAY,
    VACANT_RESERVED_BED,
    EXPIRED_RESERVATION,

    // Accommodation — informational
    RESERVATION_CREATED,
    RESERVATION_CANCELLED,
    ALLOCATION_CREATED,
    MOVE_IN_COMPLETED,
    MOVE_OUT_COMPLETED,

    // Members — actionable
    PENDING_INVITATION,
    TENANT_PROFILE_INCOMPLETE,
    MISSING_KYC_DOCUMENTS,
    MISSING_ADDRESS_PROOF,

    // Members — informational
    INVITATION_ACCEPTED,
    INVITATION_EXPIRED,
    MEMBERSHIP_APPROVED,
    MEMBERSHIP_REJECTED,
    MEMBERSHIP_REMOVED,
    MEMBERSHIP_ROLE_CHANGED,
    TENANT_PROFILE_COMPLETED,
    SPACE_DEACTIVATED,
    OWNERSHIP_TRANSFERRED,

    // Complaints
    COMPLAINT_PENDING,
    COMPLAINT_OVERDUE,
    COMPLAINT_CREATED,
    COMPLAINT_COMMENTED,
    COMPLAINT_RESOLVED,

    // Discovery contact enquiries — Admin inbox
    CONTACT_ENQUIRY,

    // Discovery contact enquiries — requester inbox (never includes owner contact)
    CONTACT_ENQUIRY_SUBMITTED,
    CONTACT_ENQUIRY_SHARED,
    CONTACT_ENQUIRY_REJECTED,
    CONTACT_ENQUIRY_EXPIRED
}
