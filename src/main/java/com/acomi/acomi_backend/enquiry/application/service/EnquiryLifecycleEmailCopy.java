package com.acomi.acomi_backend.enquiry.application.service;

import com.acomi.acomi_backend.mail.application.support.EmailTextSanitizer;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * Plain-text copy for enquiry lifecycle emails. Owner contact must never appear here.
 */
public final class EnquiryLifecycleEmailCopy {

    private static final DateTimeFormatter SUBMITTED_AT =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);

    private EnquiryLifecycleEmailCopy() {}

    public static String submittedSubject(String spaceName) {
        return titled("Enquiry submitted for ", spaceName, "your enquiry");
    }

    public static String submittedBody(String requesterName, String spaceName) {
        String name = displayName(requesterName);
        String listing = displayListing(spaceName);
        StringBuilder body = new StringBuilder();
        body.append("Hello ").append(name).append(",\n\n");
        body.append("Your enquiry for ").append(listing).append(" has been submitted successfully.\n\n");
        body.append(
                "When contact details can be shared, ACOMI emails them to this address. Check your email and the enquiry status in ACOMI.\n\n");
        body.append("Regards,\n");
        body.append("ACOMI Support\n");
        return body.toString();
    }

    public static String rejectedSubject(String spaceName) {
        return titled("Enquiry update for ", spaceName, "your enquiry");
    }

    public static String rejectedBody(String requesterName, String spaceName) {
        String name = displayName(requesterName);
        String listing = displayListing(spaceName);
        StringBuilder body = new StringBuilder();
        body.append("Hello ").append(name).append(",\n\n");
        body.append("Your enquiry for ")
                .append(listing)
                .append(" was reviewed by ACOMI and could not be fulfilled at this time.\n\n");
        body.append("You can continue exploring available places on ACOMI.\n\n");
        body.append("Regards,\n");
        body.append("ACOMI Support\n");
        return body.toString();
    }

    public static String supportSubject(String spaceName) {
        return titled("New enquiry received for ", spaceName, "a listing");
    }

    public static String supportBody(
            String spaceName,
            SpaceType spaceType,
            String requesterName,
            String requesterEmail,
            String requesterMobile,
            UUID enquiryId,
            LocalDateTime requestedAt) {
        return supportBody(
                spaceName,
                spaceType,
                requesterName,
                requesterEmail,
                requesterMobile,
                enquiryId,
                requestedAt,
                false);
    }

    public static String supportBody(
            String spaceName,
            SpaceType spaceType,
            String requesterName,
            String requesterEmail,
            String requesterMobile,
            UUID enquiryId,
            LocalDateTime requestedAt,
            boolean automaticallyShared) {
        StringBuilder body = new StringBuilder();
        body.append("A new enquiry has been submitted.\n\n");
        body.append("Space: ").append(EmailTextSanitizer.plainLine(spaceName)).append('\n');
        String type = spaceTypeLabel(spaceType);
        if (!type.isBlank()) {
            body.append("Type: ").append(type).append('\n');
        }
        body.append('\n');
        body.append("Requester: ").append(EmailTextSanitizer.plainLine(requesterName)).append('\n');
        body.append("Requester email: ").append(EmailTextSanitizer.plainLine(requesterEmail)).append('\n');
        String mobile = EmailTextSanitizer.plainLine(requesterMobile);
        if (!mobile.isBlank()) {
            body.append("Requester mobile: ").append(mobile).append('\n');
        }
        body.append('\n');
        if (enquiryId != null) {
            body.append("Enquiry ID: ").append(enquiryId).append('\n');
        }
        if (requestedAt != null) {
            body.append("Submitted at: ").append(requestedAt.format(SUBMITTED_AT)).append('\n');
        }
        body.append('\n');
        if (automaticallyShared) {
            body.append("This enquiry was shared automatically. No admin action is required unless you need to review the listing.\n\n");
        } else {
            body.append("Please review the enquiry in the ACOMI admin panel.\n\n");
        }
        body.append("Regards,\n");
        body.append("ACOMI\n");
        return body.toString();
    }

    static String spaceTypeLabel(SpaceType type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case PG -> "PG";
            case HOSTEL -> "Hostel";
            case RENTAL -> "Rental";
            case CO_LIVING -> "Co-living";
            case MESS -> "Mess";
        };
    }

    private static String titled(String prefix, String spaceName, String fallback) {
        String listing = EmailTextSanitizer.header(spaceName);
        if (listing.isBlank()) {
            listing = fallback;
        }
        return EmailTextSanitizer.subject("ACOMI – " + prefix + listing);
    }

    private static String displayName(String requesterName) {
        String name = EmailTextSanitizer.plainLine(requesterName);
        return name.isBlank() ? "there" : name;
    }

    private static String displayListing(String spaceName) {
        String listing = EmailTextSanitizer.plainLine(spaceName);
        return listing.isBlank() ? "this listing" : listing;
    }
}
