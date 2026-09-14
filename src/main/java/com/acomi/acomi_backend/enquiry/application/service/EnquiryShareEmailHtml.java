package com.acomi.acomi_backend.enquiry.application.service;

import com.acomi.acomi_backend.enquiry.api.dto.response.OwnerContactResponse;
import com.acomi.acomi_backend.mail.application.support.EmailTextSanitizer;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

/**
 * Enquiry-share HTML aligned to the ACOMI contact-details mail mock:
 * brand header, listing card with icons, mint owner card, sign-off footer.
 * Table-based so Gmail and Outlook render it consistently.
 */
final class EnquiryShareEmailHtml {

    private static final String HEADER = "#0F6B4C";
    private static final String HEADER_MUTED = "#C9E8D8";
    private static final String PRIMARY = "#25D366";
    private static final String PAGE = "#F3F5F8";
    private static final String SURFACE = "#FFFFFF";
    private static final String MINT = "#E8F7F0";
    private static final String MINT_PILL = "#D8F3E6";
    private static final String MAP_PILL = "#E7F6EE";
    private static final String FOOTER_BG = "#F7F8FA";
    private static final String BORDER = "#E6E9EE";
    private static final String DIVIDER = "#EEF1F4";
    private static final String NAVY = "#0B1B2B";
    private static final String MUTED = "#6B7280";
    private static final String SOCIAL = "#8A9AA8";
    private static final String SITE = "https://www.acomi.in";
    private static final String ICONS8 = "https://img.icons8.com/ios/50/";
    private static final String ICONS8_COLOR = "https://img.icons8.com/color/48/";
    private static final String ICONS8_GLYPH = "https://img.icons8.com/ios-glyphs/30/ffffff/";

    private EnquiryShareEmailHtml() {}

    static String render(EnquiryMailMessage message) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang=\"en\" xmlns=\"http://www.w3.org/1999/xhtml\"><head>")
                .append("<meta charset=\"UTF-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
                .append("<title>")
                .append(EmailTextSanitizer.html(EnquiryContactEmailComposer.subject(message)))
                .append("</title></head>")
                .append("<body style=\"margin:0;padding:0;background:")
                .append(PAGE)
                .append(";font-family:Arial,Helvetica,sans-serif;color:")
                .append(NAVY)
                .append(";\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:")
                .append(PAGE)
                .append(";padding:28px 12px;\"><tr><td align=\"center\">")
                .append("<table role=\"presentation\" width=\"520\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:520px;width:100%;background:")
                .append(SURFACE)
                .append(";border-radius:20px;overflow:hidden;box-shadow:0 10px 30px rgba(15,23,42,0.08);\">")
                .append(header())
                .append(intro(message))
                .append(listingCard(message))
                .append(ownerCard(message.ownerContact()))
                .append(closing())
                .append(footer())
                .append("</table></td></tr></table></body></html>");
        return html.toString();
    }

    private static String header() {
        return "<tr><td style=\"background:"
                + HEADER
                + ";padding:22px 24px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr>"
                + "<td valign=\"middle\" style=\"padding-right:12px;\">"
                + "<a href=\""
                + SITE
                + "\" style=\"text-decoration:none;color:#FFFFFF;\">"
                + "<div style=\"font-size:22px;line-height:1.1;font-weight:800;letter-spacing:0.04em;color:#FFFFFF;\">ACOMI</div>"
                + "<div style=\"margin-top:4px;font-size:11px;line-height:1.3;color:"
                + HEADER_MUTED
                + ";\">Homes Made Simpler</div></a></td>"
                + "<td valign=\"middle\" align=\"right\" style=\"font-size:11px;line-height:1.45;color:"
                + HEADER_MUTED
                + ";white-space:nowrap;\">"
                + "PG&nbsp;|&nbsp;Mess&nbsp;|&nbsp;Hostels&nbsp;|&nbsp;Rentals<br>"
                + "Manage&nbsp;&bull;&nbsp;Connect&nbsp;&bull;&nbsp;Grow"
                + "</td></tr></table></td></tr>";
    }

    private static String intro(EnquiryMailMessage message) {
        String greeting = EnquiryContactEmailComposer.display(message.requesterName());
        StringBuilder intro = new StringBuilder();
        intro.append("<tr><td style=\"background:")
                .append(SURFACE)
                .append(";padding:28px 24px 8px 24px;\">");
        if (greeting != null) {
            intro.append("<div style=\"font-size:22px;line-height:1.3;font-weight:800;color:")
                    .append(NAVY)
                    .append(";\">Hi ")
                    .append(EmailTextSanitizer.html(greeting))
                    .append(",</div>");
        }
        intro.append("<div style=\"margin-top:10px;font-size:14px;line-height:1.6;color:")
                .append(MUTED)
                .append(";\">Your enquiry has been reviewed by ACOMI. Here are the listing and owner details you can use.</div>")
                .append("</td></tr>");
        return intro.toString();
    }

    private static String listingCard(EnquiryMailMessage message) {
        String spaceName = EnquiryContactEmailComposer.display(message.spaceName());
        String type = EnquiryContactEmailComposer.display(EnquiryContactEmailComposer.typeLabel(message.spaceType()));
        String mapUrl = EnquiryContactEmailComposer.display(message.listingOrEmpty().mapUrl());
        List<IconRow> rows = listingRows(message);
        if (spaceName == null && type == null && mapUrl == null && rows.isEmpty()) {
            return "";
        }

        StringBuilder card = new StringBuilder();
        card.append("<tr><td style=\"background:")
                .append(SURFACE)
                .append(";padding:16px 24px 8px 24px;\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"border:1px solid ")
                .append(BORDER)
                .append(";border-radius:16px;\">")
                .append("<tr><td style=\"padding:16px 18px 8px 18px;\">")
                .append(sectionPill("LISTING"))
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin-top:10px;\"><tr>");
        card.append("<td valign=\"top\" style=\"padding-right:10px;\">");
        if (spaceName != null) {
            card.append("<div style=\"font-size:20px;line-height:1.3;font-weight:800;color:")
                    .append(NAVY)
                    .append(";\">")
                    .append(EmailTextSanitizer.html(spaceName))
                    .append("</div>");
        }
        if (type != null) {
            card.append("<div style=\"margin-top:10px;\">")
                    .append(typePill(type))
                    .append("</div>");
        }
        card.append("</td>");
        if (mapUrl != null) {
            card.append("<td valign=\"middle\" align=\"right\" width=\"118\">")
                    .append(mapButton(mapUrl))
                    .append("</td>");
        }
        card.append("</tr></table></td></tr>");
        if (!rows.isEmpty()) {
            card.append("<tr><td style=\"padding:4px 14px 10px 14px;\">")
                    .append(iconRows(rows))
                    .append("</td></tr>");
        }
        card.append("</table></td></tr>");
        return card.toString();
    }

    private static String ownerCard(OwnerContactResponse contact) {
        List<IconRow> rows = ownerRows(contact);
        if (rows.isEmpty()) {
            return "";
        }
        return "<tr><td style=\"background:"
                + SURFACE
                + ";padding:12px 24px 8px 24px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:"
                + MINT
                + ";border-radius:16px;\">"
                + "<tr><td style=\"padding:16px 18px 8px 18px;\">"
                + sectionPill("OWNER CONTACT")
                + "</td></tr>"
                + "<tr><td style=\"padding:0 14px 10px 14px;\">"
                + iconRows(rows)
                + "</td></tr></table></td></tr>";
    }

    private static String closing() {
        return "<tr><td style=\"background:"
                + SURFACE
                + ";padding:16px 24px 8px 24px;\">"
                + "<div style=\"font-size:13px;line-height:1.55;color:"
                + MUTED
                + ";\">Please use these details only for the purpose of your enquiry.</div>"
                + "<div style=\"margin-top:22px;font-size:14px;line-height:1.55;color:"
                + NAVY
                + ";\">Thanks,<br>"
                + "<strong>ACOMI Support Team</strong><br>"
                + "<span style=\"color:"
                + MUTED
                + ";font-size:13px;\">Simplifying PG &amp; Rental Living</span></div>"
                + "</td></tr>";
    }

    private static String footer() {
        String year = String.valueOf(Year.now().getValue());
        return "<tr><td style=\"background:"
                + FOOTER_BG
                + ";padding:16px 24px 20px 24px;border-top:1px solid "
                + BORDER
                + ";\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr>"
                + "<td valign=\"middle\" style=\"font-size:11px;line-height:1.5;color:"
                + MUTED
                + ";\">&copy; "
                + year
                + " ACOMI. All rights reserved.<br>Homes Made Simpler.</td>"
                + "<td valign=\"middle\" align=\"right\">"
                + socials()
                + "</td></tr></table></td></tr>";
    }

    private static List<IconRow> listingRows(EnquiryMailMessage message) {
        EnquiryListingDetails listing = message.listingOrEmpty();
        List<IconRow> rows = new ArrayList<>();
        addTextRow(rows, "users", "Gender", "Gender", listing.genderPolicy());
        addTextRow(rows, "building-2", "Sharing", "Sharing ratio", listing.sharingNotes());
        addTextRow(rows, "star", "Amenities", "Amenities", listing.amenities());
        addTextRow(rows, "file-text", "Description", "Description", listing.description());
        boolean structured = addTextRow(rows, "map", "Address", "Address", listing.addressLine())
                | addTextRow(rows, "map", "City", "City", listing.city())
                | addTextRow(rows, "map", "State", "State", listing.state())
                | addTextRow(rows, "hash", "Pincode", "Pincode", listing.pincode());
        if (!structured) {
            addTextRow(
                    rows,
                    "map",
                    "Location",
                    "Location",
                    EnquiryContactEmailComposer.locationValue(listing.location(), message.spaceAddress()));
        }
        addTextRow(rows, "utensils", "Food", "Food included in rent", listing.foodIncluded());
        if (message.spaceType() == SpaceType.MESS) {
            boolean priced = addTextRow(rows, "banknote", "Price", "Monthly price", listing.monthlyPrice());
            addTextRow(rows, "banknote", "Meal price", "Meal price", listing.mealPrice());
            if (priced) {
                addTextRow(rows, "banknote", "Price basis", "Price basis", listing.priceBasis());
            }
        } else {
            boolean priced = addTextRow(rows, "banknote", "Price", "Starting price", listing.startingPrice());
            if (priced) {
                addTextRow(rows, "banknote", "Price basis", "Price basis", listing.priceBasis());
            }
        }
        addTextRow(rows, "users", "Capacity", "Capacity", listing.capacity());
        return rows;
    }

    private static List<IconRow> ownerRows(OwnerContactResponse contact) {
        List<IconRow> rows = new ArrayList<>();
        addTextRow(rows, "user", "Name", "Name", contact != null ? contact.getOwnerName() : null);
        addPhoneRow(rows, "phone", "Mobile", "Mobile", contact != null ? contact.getMobileNumber() : null);
        addPhoneRow(
                rows,
                "whatsapp",
                "WhatsApp",
                "Alternate mobile",
                contact != null ? contact.getAlternateMobileNumber() : null);
        addPhoneRow(
                rows,
                "phone",
                "Phone",
                "Additional contact",
                contact != null ? contact.getAdditionalMobileNumber() : null);
        String email = EnquiryContactEmailComposer.display(contact != null ? contact.getEmail() : null);
        if (email != null) {
            rows.add(new IconRow(
                    "mail",
                    "Email",
                    "Email",
                    "<a href=\"mailto:"
                            + EmailTextSanitizer.html(email)
                            + "\" style=\"color:"
                            + NAVY
                            + ";text-decoration:none;font-weight:700;\">"
                            + EmailTextSanitizer.html(email)
                            + "</a>"));
        }
        return rows;
    }

    private static boolean addTextRow(List<IconRow> rows, String icon, String alt, String label, String value) {
        String shown = EnquiryContactEmailComposer.display(value);
        if (shown == null) {
            return false;
        }
        rows.add(new IconRow(icon, alt, label, EmailTextSanitizer.html(shown)));
        return true;
    }

    private static void addPhoneRow(List<IconRow> rows, String icon, String alt, String label, String mobile) {
        String shown = EnquiryContactEmailComposer.displayMobile(mobile);
        if (shown == null) {
            return;
        }
        String telHref = shown.replaceAll("[^0-9+]", "");
        String value = telHref.isBlank()
                ? EmailTextSanitizer.html(shown)
                : "<a href=\"tel:"
                        + EmailTextSanitizer.html(telHref)
                        + "\" style=\"color:"
                        + NAVY
                        + ";text-decoration:none;font-weight:700;\">"
                        + EmailTextSanitizer.html(shown)
                        + "</a>";
        rows.add(new IconRow(icon, alt, label, value));
    }

    private static String iconRows(List<IconRow> rows) {
        StringBuilder html = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            IconRow row = rows.get(i);
            boolean last = i == rows.size() - 1;
            String border = last ? "none" : "1px solid " + DIVIDER;
            html.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr>")
                    .append("<td width=\"24\" valign=\"middle\" style=\"padding:11px 10px 11px 2px;border-bottom:")
                    .append(border)
                    .append(";\">")
                    .append(iconImg(row.icon, row.alt))
                    .append("</td>")
                    .append("<td valign=\"middle\" style=\"padding:11px 8px;border-bottom:")
                    .append(border)
                    .append(";font-size:13px;color:")
                    .append(MUTED)
                    .append(";\">")
                    .append(EmailTextSanitizer.html(row.label))
                    .append("</td>")
                    .append("<td valign=\"middle\" align=\"right\" style=\"padding:11px 2px 11px 8px;border-bottom:")
                    .append(border)
                    .append(";font-size:13px;line-height:1.4;color:")
                    .append(NAVY)
                    .append(";font-weight:700;\">")
                    .append(row.valueHtml)
                    .append("</td></tr></table>");
        }
        return html.toString();
    }

    private static String sectionPill(String label) {
        return "<span style=\"display:inline-block;background:"
                + MINT_PILL
                + ";color:"
                + HEADER
                + ";font-size:10px;font-weight:800;letter-spacing:0.08em;padding:4px 8px;border-radius:999px;\">"
                + EmailTextSanitizer.html(label)
                + "</span>";
    }

    private static String typePill(String type) {
        return "<span style=\"display:inline-block;background:"
                + PRIMARY
                + ";color:#FFFFFF;font-size:11px;font-weight:800;letter-spacing:0.04em;padding:4px 10px;border-radius:999px;\">"
                + EmailTextSanitizer.html(type)
                + "</span>";
    }

    private static String mapButton(String mapUrl) {
        String href = EmailTextSanitizer.html(mapUrl);
        return "<a href=\""
                + href
                + "\" style=\"text-decoration:none;\">"
                + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:"
                + MAP_PILL
                + ";border-radius:999px;\"><tr>"
                + "<td valign=\"middle\" style=\"padding:8px 4px 8px 12px;\">"
                + img(ICONS8 + "0F6B4C/marker.png", "Map", 14)
                + "</td>"
                + "<td valign=\"middle\" style=\"padding:8px 12px 8px 4px;font-size:12px;line-height:14px;font-weight:700;color:"
                + HEADER
                + ";\">Open map</td>"
                + "</tr></table></a>";
    }

    private static String socials() {
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" align=\"right\"><tr>"
                + social("linkedin", "LinkedIn")
                + social("instagram", "Instagram")
                + social("youtube", "YouTube")
                + "</tr></table>";
    }

    private static String social(String name, String alt) {
        return "<td style=\"padding-left:8px;\">"
                + "<a href=\""
                + SITE
                + "\" style=\"text-decoration:none;\">"
                + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\"><tr>"
                + "<td width=\"22\" height=\"22\" align=\"center\" valign=\"middle\" style=\"background:"
                + SOCIAL
                + ";border-radius:11px;\">"
                + img(ICONS8_GLYPH + socialGlyph(name) + ".png", alt, 12)
                + "</td></tr></table></a></td>";
    }

    private static String socialGlyph(String name) {
        return switch (name) {
            case "instagram" -> "instagram-new";
            case "youtube" -> "youtube-play";
            default -> name;
        };
    }

    private static String iconImg(String name, String alt) {
        if ("whatsapp".equals(name)) {
            return img(ICONS8_COLOR + "whatsapp--v1.png", alt, 16);
        }
        return img(ICONS8 + "6B7280/" + icons8Name(name) + ".png", alt, 16);
    }

    private static String icons8Name(String name) {
        return switch (name) {
            case "users" -> "conference-call";
            case "building-2" -> "home";
            case "star" -> "star--v1";
            case "map-pin" -> "marker";
            case "file-text" -> "align-left";
            case "utensils" -> "restaurant";
            case "banknote" -> "money";
            case "hash" -> "password";
            default -> name;
        };
    }

    private static String img(String src, String alt, int size) {
        return "<img src=\""
                + src
                + "\" width=\""
                + size
                + "\" height=\""
                + size
                + "\" alt=\""
                + EmailTextSanitizer.html(alt)
                + "\" style=\"display:inline-block;vertical-align:middle;border:0;outline:none;text-decoration:none;\">";
    }

    private record IconRow(String icon, String alt, String label, String valueHtml) {}
}
