package com.acomi.acomi_backend.enquiry.application.service;

import com.acomi.acomi_backend.common.util.MobileNumberNormalizer;
import com.acomi.acomi_backend.enquiry.api.dto.response.OwnerContactResponse;
import com.acomi.acomi_backend.mail.application.support.EmailTextSanitizer;
import com.acomi.acomi_backend.registration.application.AdminLeadDefaults;
import com.acomi.acomi_backend.space.domain.model.SpaceType;

/**
 * Enquiry-share email. Owner contact belongs only in this authorised
 * channel — never in logs, audit rows, or in-app notifications.
 */
public final class EnquiryContactEmailComposer {

    private EnquiryContactEmailComposer() {}

    public static String subject(EnquiryMailMessage message) {
        String spaceName = EmailTextSanitizer.header(message != null ? message.spaceName() : null);
        if (spaceName.isBlank()) {
            return "ACOMI – Contact details for your enquiry";
        }
        return EmailTextSanitizer.subject("ACOMI – Contact details for " + spaceName);
    }

    public static String body(EnquiryMailMessage message) {
        StringBuilder body = new StringBuilder();
        body.append("Your enquiry has been reviewed by ACOMI.\n\n");
        appendPlain(body, "Property", display(message.spaceName()));
        appendPlain(body, "Type", display(typeLabel(message.spaceType())));
        appendListingPlain(body, message);
        body.append('\n');
        body.append("Owner contact\n");
        appendOwnerPlain(body, message.ownerContact());
        body.append('\n');
        body.append("Please use these details only for the purpose of your enquiry.\n");
        return body.toString();
    }

    public static String htmlBody(EnquiryMailMessage message) {
        return EnquiryShareEmailHtml.render(message);
    }

    private static void appendListingPlain(StringBuilder body, EnquiryMailMessage message) {
        EnquiryListingDetails listing = message.listingOrEmpty();
        appendPlain(body, "Description", display(listing.description()));
        boolean structuredAddress = appendPlain(body, "Address", display(listing.addressLine()))
                | appendPlain(body, "City", display(listing.city()))
                | appendPlain(body, "State", display(listing.state()))
                | appendPlain(body, "Pincode", display(listing.pincode()));
        if (!structuredAddress) {
            appendPlain(body, "Location", locationValue(listing.location(), message.spaceAddress()));
        }
        appendPlain(body, "Map link", display(listing.mapUrl()));
        appendPlain(body, "Gender", display(listing.genderPolicy()));
        appendPlain(body, "Food included in rent", display(listing.foodIncluded()));
        if (message.spaceType() == SpaceType.MESS) {
            boolean priced = appendPlain(body, "Monthly price", display(listing.monthlyPrice()));
            appendPlain(body, "Meal price", display(listing.mealPrice()));
            if (priced) {
                appendPlain(body, "Price basis", display(listing.priceBasis()));
            }
        } else {
            boolean priced = appendPlain(body, "Starting price", display(listing.startingPrice()));
            if (priced) {
                appendPlain(body, "Price basis", display(listing.priceBasis()));
            }
        }
        appendPlain(body, "Capacity", display(listing.capacity()));
        appendPlain(body, "Sharing notes", display(listing.sharingNotes()));
        appendPlain(body, "Amenities", display(listing.amenities()));
    }

    private static void appendOwnerPlain(StringBuilder body, OwnerContactResponse contact) {
        appendPlain(body, "Name", display(contact != null ? contact.getOwnerName() : null));
        appendPlain(body, "Mobile", displayMobile(contact != null ? contact.getMobileNumber() : null));
        appendPlain(body, "Alternate mobile", displayMobile(contact != null ? contact.getAlternateMobileNumber() : null));
        appendPlain(body, "Additional contact", displayMobile(contact != null ? contact.getAdditionalMobileNumber() : null));
        appendPlain(body, "Email", display(contact != null ? contact.getEmail() : null));
    }

    private static boolean appendPlain(StringBuilder body, String label, String value) {
        if (value == null) {
            return false;
        }
        body.append(label).append(": ").append(value).append('\n');
        return true;
    }

    static String display(String value) {
        String line = EmailTextSanitizer.plainLine(value);
        if (line.isBlank()
                || line.equalsIgnoreCase(AdminLeadDefaults.UNKNOWN_OWNER)
                || "—".equals(line)
                || "-".equals(line)
                || "–".equals(line)
                || AdminLeadDefaults.PLACEHOLDER_MOBILE.equals(line)) {
            return null;
        }
        return line;
    }

    /** Owner contact mobiles always include +91 for dialling from email clients. */
    static String displayMobile(String value) {
        String shown = display(value);
        if (shown == null) {
            return null;
        }
        String formatted = MobileNumberNormalizer.formatWithCountryCode(shown);
        return formatted != null ? formatted : shown;
    }

    static String locationValue(String preferred, String fallback) {
        String value = display(preferred);
        if (value != null && !AdminLeadDefaults.PLACEHOLDER_PINCODE.equals(value)) {
            return value;
        }
        String spaceAddress = display(fallback);
        if (spaceAddress == null || AdminLeadDefaults.PLACEHOLDER_PINCODE.equals(spaceAddress)) {
            return null;
        }
        return spaceAddress;
    }

    static String typeLabel(SpaceType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case PG -> "PG";
            case HOSTEL -> "Hostel";
            case RENTAL -> "Rental";
            case CO_LIVING -> "Co-living";
            case MESS -> "Mess";
        };
    }
}
