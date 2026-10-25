package com.acomi.acomi_backend.admin.application.support;

import com.acomi.acomi_backend.notification.domain.model.NotificationEntityType;
import com.acomi.acomi_backend.notification.domain.model.NotificationType;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Describes every {@link NotificationType} the admin test center can send.
 * Entries are fixtures. Sending one does not create a payment, member, enquiry, or complaint.
 */
public final class NotificationTestCatalog {

    public static final String TEST_ACTION_ROUTE = "NotificationTest";

    private NotificationTestCatalog() {}

    public record Entry(
            NotificationType type,
            String name,
            String description,
            String category,
            List<String> channels,
            String implementationStatus,
            boolean safeToTest,
            NotificationEntityType entityType,
            String productionActionRoute) {}

    public static List<Entry> all() {
        return Arrays.stream(NotificationType.values()).map(NotificationTestCatalog::entry).toList();
    }

    public static Entry entry(NotificationType type) {
        String name = displayName(type);
        return new Entry(
                type,
                name,
                "Sends the " + name
                        + " payload to this admin account only. It does not change payments, members, "
                        + "enquiries, meals, occupancy, or complaints.",
                category(type),
                List.of("FCM_MOBILE", "IN_APP"),
                "PAYLOAD_AND_PUSH",
                true,
                entityType(type),
                productionActionRoute(type));
    }

    public static String displayName(NotificationType type) {
        String[] parts = type.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder text = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!text.isEmpty()) {
                text.append(' ');
            }
            text.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                text.append(part.substring(1));
            }
        }
        return text.toString();
    }

    static String category(NotificationType type) {
        return switch (type) {
            case PAYMENT_NEEDS_REVIEW, PAYMENT_NEEDS_UPDATE, PAYMENT_OVERDUE, PAYMENT_SUBMITTED,
                    PAYMENT_APPROVED, PAYMENT_REJECTED, PAYMENT_UPDATE_REQUESTED, PAYMENT_REMINDER_SENT ->
                    "Payments";
            case MEAL_POLL_NOT_PUBLISHED, MEAL_RESPONSES_BELOW_THRESHOLD, MENU_NOT_PLANNED,
                    MENU_DRAFT_PENDING_PUBLISH, SUBSCRIPTION_ACTIVATION_PENDING, MENU_PUBLISHED,
                    MEAL_POLL_PUBLISHED, MEAL_POLL_REMINDER, SUBSCRIPTION_ACTIVATION_APPROVED,
                    SUBSCRIPTION_ACTIVATION_REJECTED, MEAL_BALANCE_UPDATED, MEAL_PARTICIPATION_CHANGED ->
                    "Meals";
            case RESERVATION_STARTING_TODAY, MOVE_IN_SCHEDULED_TODAY, MOVE_OUT_SCHEDULED_TODAY,
                    VACANT_RESERVED_BED, EXPIRED_RESERVATION, RESERVATION_CREATED, RESERVATION_CANCELLED,
                    ALLOCATION_CREATED, MOVE_IN_COMPLETED, MOVE_OUT_COMPLETED ->
                    "Occupancy";
            case PENDING_INVITATION, TENANT_PROFILE_INCOMPLETE, MISSING_KYC_DOCUMENTS, MISSING_ADDRESS_PROOF,
                    INVITATION_ACCEPTED, INVITATION_EXPIRED, MEMBERSHIP_APPROVED, MEMBERSHIP_REJECTED,
                    MEMBERSHIP_REMOVED, MEMBERSHIP_ROLE_CHANGED, TENANT_PROFILE_COMPLETED, SPACE_DEACTIVATED,
                    OWNERSHIP_TRANSFERRED ->
                    "Members";
            case COMPLAINT_PENDING, COMPLAINT_OVERDUE, COMPLAINT_CREATED, COMPLAINT_COMMENTED, COMPLAINT_RESOLVED ->
                    "Complaints";
            case CONTACT_ENQUIRY, CONTACT_ENQUIRY_RECEIVED, CONTACT_ENQUIRY_SUBMITTED, CONTACT_ENQUIRY_SHARED,
                    CONTACT_ENQUIRY_REJECTED, CONTACT_ENQUIRY_EXPIRED, PHOTO_REQUEST_RECEIVED ->
                    "Enquiries";
            case INQUIRY_CREDIT_PAYMENT_PENDING, INQUIRY_CREDIT_PAYMENT_APPROVED, INQUIRY_CREDIT_PAYMENT_REJECTED ->
                    "Inquiry credits";
            case PASSWORD_CHANGED -> "Account";
        };
    }

    static NotificationEntityType entityType(NotificationType type) {
        return switch (type) {
            case PAYMENT_NEEDS_REVIEW, PAYMENT_NEEDS_UPDATE, PAYMENT_OVERDUE, PAYMENT_SUBMITTED,
                    PAYMENT_APPROVED, PAYMENT_REJECTED, PAYMENT_UPDATE_REQUESTED, PAYMENT_REMINDER_SENT ->
                    NotificationEntityType.PAYMENT;
            case MEAL_POLL_NOT_PUBLISHED, MEAL_RESPONSES_BELOW_THRESHOLD, MEAL_POLL_PUBLISHED, MEAL_POLL_REMINDER ->
                    NotificationEntityType.MEAL_POLL;
            case MENU_NOT_PLANNED, MENU_DRAFT_PENDING_PUBLISH, MENU_PUBLISHED -> NotificationEntityType.DAILY_MENU;
            case SUBSCRIPTION_ACTIVATION_PENDING, SUBSCRIPTION_ACTIVATION_APPROVED,
                    SUBSCRIPTION_ACTIVATION_REJECTED, MEAL_BALANCE_UPDATED, MEAL_PARTICIPATION_CHANGED ->
                    NotificationEntityType.SUBSCRIPTION;
            case RESERVATION_STARTING_TODAY, MOVE_IN_SCHEDULED_TODAY, MOVE_OUT_SCHEDULED_TODAY, VACANT_RESERVED_BED,
                    EXPIRED_RESERVATION, RESERVATION_CREATED, RESERVATION_CANCELLED, ALLOCATION_CREATED,
                    MOVE_IN_COMPLETED, MOVE_OUT_COMPLETED ->
                    NotificationEntityType.OCCUPANCY;
            case PENDING_INVITATION, INVITATION_ACCEPTED, INVITATION_EXPIRED -> NotificationEntityType.INVITATION;
            case TENANT_PROFILE_INCOMPLETE, MISSING_KYC_DOCUMENTS, MISSING_ADDRESS_PROOF, MEMBERSHIP_APPROVED,
                    MEMBERSHIP_REJECTED, MEMBERSHIP_REMOVED, MEMBERSHIP_ROLE_CHANGED, TENANT_PROFILE_COMPLETED,
                    OWNERSHIP_TRANSFERRED ->
                    NotificationEntityType.MEMBER;
            case SPACE_DEACTIVATED, PHOTO_REQUEST_RECEIVED -> NotificationEntityType.SPACE;
            case COMPLAINT_PENDING, COMPLAINT_OVERDUE, COMPLAINT_CREATED, COMPLAINT_COMMENTED, COMPLAINT_RESOLVED ->
                    NotificationEntityType.COMPLAINT;
            case CONTACT_ENQUIRY, CONTACT_ENQUIRY_RECEIVED, CONTACT_ENQUIRY_SUBMITTED, CONTACT_ENQUIRY_SHARED,
                    CONTACT_ENQUIRY_REJECTED, CONTACT_ENQUIRY_EXPIRED ->
                    NotificationEntityType.SPACE_ENQUIRY;
            case INQUIRY_CREDIT_PAYMENT_PENDING, INQUIRY_CREDIT_PAYMENT_APPROVED, INQUIRY_CREDIT_PAYMENT_REJECTED ->
                    NotificationEntityType.INQUIRY_CREDIT_PURCHASE;
            case PASSWORD_CHANGED -> NotificationEntityType.USER_ACCOUNT;
        };
    }

    static String productionActionRoute(NotificationType type) {
        return switch (type) {
            case PAYMENT_NEEDS_REVIEW, PAYMENT_NEEDS_UPDATE, PAYMENT_OVERDUE, PAYMENT_SUBMITTED, PAYMENT_APPROVED,
                    PAYMENT_REJECTED, PAYMENT_UPDATE_REQUESTED, PAYMENT_REMINDER_SENT ->
                    "PaymentDetail";
            case CONTACT_ENQUIRY, CONTACT_ENQUIRY_RECEIVED -> "AdminEnquiryDetail";
            case CONTACT_ENQUIRY_SUBMITTED, CONTACT_ENQUIRY_SHARED, CONTACT_ENQUIRY_REJECTED, CONTACT_ENQUIRY_EXPIRED ->
                    "MyEnquiries";
            case PHOTO_REQUEST_RECEIVED -> "FindAPlaceDetail";
            case INQUIRY_CREDIT_PAYMENT_PENDING, INQUIRY_CREDIT_PAYMENT_APPROVED, INQUIRY_CREDIT_PAYMENT_REJECTED ->
                    "InquiryCredits";
            case PASSWORD_CHANGED -> "Profile";
            case PENDING_INVITATION, INVITATION_EXPIRED, MEMBERSHIP_REJECTED -> "AcceptInvitations";
            case MEMBERSHIP_REMOVED, SPACE_DEACTIVATED, OWNERSHIP_TRANSFERRED -> "MySpaces";
            default -> type.name();
        };
    }
}
