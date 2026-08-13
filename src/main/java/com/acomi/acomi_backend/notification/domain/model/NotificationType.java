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

    // Meals — actionable
    MEAL_POLL_NOT_PUBLISHED,
    MEAL_RESPONSES_BELOW_THRESHOLD,
    MENU_NOT_PLANNED,
    MENU_DRAFT_PENDING_PUBLISH,
    SUBSCRIPTION_ACTIVATION_PENDING,

    // Meals — informational
    MEAL_POLL_PUBLISHED,
    MEAL_POLL_REMINDER,

    // Accommodation — actionable
    RESERVATION_STARTING_TODAY,
    MOVE_IN_SCHEDULED_TODAY,
    MOVE_OUT_SCHEDULED_TODAY,
    VACANT_RESERVED_BED,
    EXPIRED_RESERVATION,

    // Accommodation — informational
    RESERVATION_CREATED,
    MOVE_IN_COMPLETED,
    MOVE_OUT_COMPLETED,

    // Members — actionable
    PENDING_INVITATION,
    TENANT_PROFILE_INCOMPLETE,
    MISSING_KYC_DOCUMENTS,
    MISSING_ADDRESS_PROOF,

    // Members — informational
    INVITATION_ACCEPTED,
    TENANT_PROFILE_COMPLETED,

    // Complaints
    COMPLAINT_PENDING,
    COMPLAINT_OVERDUE,
    COMPLAINT_CREATED,
    COMPLAINT_COMMENTED,
    COMPLAINT_RESOLVED
}
