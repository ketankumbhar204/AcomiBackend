package com.acomi.acomi_backend.enquiry.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.enquiry.api.dto.response.OwnerContactResponse;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import org.junit.jupiter.api.Test;

class EnquiryContactEmailComposerTest {

    @Test
    void subjectStripsCrLfFromSpaceName() {
        EnquiryMailMessage message = new EnquiryMailMessage(
                "ketan@example.com",
                "Ketan",
                "Sunrise PG\r\nBcc: attacker@example.com",
                SpaceType.PG,
                "Wakad",
                shareableContact());

        String subject = EnquiryContactEmailComposer.subject(message);
        assertThat(subject).doesNotContain("\r").doesNotContain("\n");
        assertThat(subject).startsWith("ACOMI – Contact details for Sunrise PG");
    }

    @Test
    void bodyIncludesOwnerContactForAuthorisedShareChannelOnly() {
        String body = EnquiryContactEmailComposer.body(new EnquiryMailMessage(
                "ketan@example.com",
                "Ketan",
                "Sunrise PG",
                SpaceType.PG,
                "Wakad",
                shareableContact()));

        assertThat(body).contains("+919991110001");
        assertThat(body).contains("Owner contact");
        assertThat(body).doesNotContain("<html");
    }

    @Test
    void bodyOmitsMissingListingAndOwnerFields() {
        String body = EnquiryContactEmailComposer.body(new EnquiryMailMessage(
                "ketan@example.com",
                "Ketan",
                "Lovely Home's PG 3",
                SpaceType.PG,
                "110001",
                OwnerContactResponse.builder()
                        .mobileNumber("9175465599")
                        .alternateMobileNumber("7722085599")
                        .available(true)
                        .build(),
                EnquiryListingDetails.empty()));

        assertThat(body).contains("Property: Lovely Home's PG 3");
        assertThat(body).contains("Mobile: +919175465599");
        assertThat(body).contains("Alternate mobile: +917722085599");
        assertThat(body).doesNotContain("Not available");
        assertThat(body).doesNotContain("Description:");
        assertThat(body).doesNotContain("Address:");
        assertThat(body).doesNotContain("Location:");
        assertThat(body).doesNotContain("Coordinates:");
        assertThat(body).doesNotContain("Name:");
        assertThat(body).doesNotContain("Email:");
        assertThat(body).doesNotContain("110001");
    }

    @Test
    void bodyIncludesStoredListingDetailsAndKeepsRealPincode() {
        EnquiryListingDetails listing = new EnquiryListingDetails(
                "Close to metro",
                "12 MG Road",
                "New Delhi",
                "Delhi",
                "110001",
                "12 MG Road, New Delhi, Delhi, 110001",
                "https://maps.google.com/?q=28.61,77.20",
                "28.61, 77.20",
                "Male",
                "Yes",
                "INR 8500",
                "Per bed",
                null,
                null,
                "12",
                "Double sharing",
                "WiFi, Parking");

        EnquiryMailMessage message = new EnquiryMailMessage(
                "ketan@example.com",
                "Ketan",
                "Sunrise PG",
                SpaceType.PG,
                "12 MG Road, New Delhi",
                shareableContact(),
                listing);
        String body = EnquiryContactEmailComposer.body(message);
        String html = EnquiryContactEmailComposer.htmlBody(message);

        assertThat(body).contains("Description: Close to metro");
        assertThat(body).contains("Address: 12 MG Road");
        assertThat(body).contains("City: New Delhi");
        assertThat(body).contains("Pincode: 110001");
        assertThat(body).contains("Map link: https://maps.google.com/?q=28.61,77.20");
        assertThat(body).contains("Starting price: INR 8500");
        assertThat(body).contains("Price basis: Per bed");
        assertThat(body).contains("Amenities: WiFi, Parking");
        assertThat(body).contains("Name: Owner A");
        assertThat(body).doesNotContain("Email:");
        assertThat(body).doesNotContain("Coordinates:");
        assertThat(body).doesNotContain("Location:");

        assertThat(html).contains("<html");
        assertThat(html).contains("#0F6B4C");
        assertThat(html).contains("Homes Made Simpler");
        assertThat(html).contains("LISTING");
        assertThat(html).contains("OWNER CONTACT");
        assertThat(html).contains("ACOMI Support Team");
        assertThat(html).contains("Open map");
        assertThat(html).contains("img.icons8.com/ios/50/0F6B4C/marker.png");
        assertThat(html).contains("12 MG Road");
        assertThat(html).doesNotContain("Coordinates");
        assertThat(html).doesNotContain("Not available");
        assertThat(html).doesNotContain("28.61, 77.20");
    }

    @Test
    void htmlEscapesListingNameAndOmitsMissingRows() {
        String html = EnquiryContactEmailComposer.htmlBody(new EnquiryMailMessage(
                "ketan@example.com",
                "Ketan",
                "Lovely Home's PG 3",
                SpaceType.PG,
                "110001",
                OwnerContactResponse.builder()
                        .mobileNumber("9175465599")
                        .alternateMobileNumber("7722085599")
                        .available(true)
                        .build(),
                new EnquiryListingDetails(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "https://maps.google.com/?q=18.6052262,73.7236231",
                        "18.6052262, 73.7236231",
                        "Mixed",
                        null,
                        null,
                        "Per bed",
                        null,
                        null,
                        null,
                        "1, 2 & 3 Sharing",
                        "Power Backup")));

        assertThat(html).contains("Lovely Home&#39;s PG 3");
        assertThat(html).contains("Hi Ketan");
        assertThat(html).contains("1, 2 &amp; 3 Sharing");
        assertThat(html).contains("Sharing ratio");
        assertThat(html).contains("Power Backup");
        assertThat(html).contains("Mixed");
        assertThat(html).contains("Open map");
        assertThat(html).contains("img.icons8.com/color/48/whatsapp--v1.png");
        assertThat(html).contains("img.icons8.com/ios/50/6B7280/conference-call.png");
        assertThat(html).contains("img.icons8.com/ios/50/6B7280/star--v1.png");
        assertThat(html).doesNotContain("Not available");
        assertThat(html).doesNotContain("Description");
        assertThat(html).doesNotContain("Coordinates");
        assertThat(html).doesNotContain("Starting price");
        assertThat(html).doesNotContain("Price basis");
        assertThat(html).doesNotContain("18.6052262, 73.7236231");
    }

    private static OwnerContactResponse shareableContact() {
        return OwnerContactResponse.builder()
                .ownerName("Owner A")
                .mobileNumber("9991110001")
                .available(true)
                .build();
    }
}
