package com.acomi.acomi_backend.enquiry.api.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.enquiry.domain.model.EnquiryRequesterType;
import com.acomi.acomi_backend.enquiry.domain.model.SpaceEnquiryStatus;
import com.acomi.acomi_backend.notification.api.dto.response.UserNotificationResponse;
import com.acomi.acomi_backend.space.api.dto.response.DiscoverSpaceCardResponse;
import com.acomi.acomi_backend.space.api.dto.response.DiscoverSpaceDetailResponse;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnquiryPrivacySerializationTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void discoverCard_doesNotLeakOwnerContact() throws Exception {
        DiscoverSpaceCardResponse card = DiscoverSpaceCardResponse.builder()
                .spaceId(UUID.randomUUID())
                .name("Sunrise PG")
                .type(SpaceType.PG)
                .address("Wakad, Pune")
                .foodIncludedInRent(true)
                .alreadyMember(false)
                .build();

        String json = mapper.writeValueAsString(card);
        assertThat(json).doesNotContain("mobile");
        assertThat(json).doesNotContain("owner");
        assertThat(json).doesNotContain("email");
        assertThat(json).doesNotContain("contact");
        assertThat(json).contains("Sunrise PG");
    }

    @Test
    void discoverDetail_doesNotLeakOwnerContact() throws Exception {
        DiscoverSpaceDetailResponse detail = DiscoverSpaceDetailResponse.builder()
                .spaceId(UUID.randomUUID())
                .name("Sunrise PG")
                .type(SpaceType.PG)
                .addressLine("12, Datta Mandir Road")
                .city("Wakad")
                .startingPrice(new java.math.BigDecimal("8500.00"))
                .alreadyMember(false)
                .ownedByCurrentUser(false)
                .build();

        String json = mapper.writeValueAsString(detail);
        assertThat(json).doesNotContain("mobile");
        assertThat(json).doesNotContain("ownerName");
        assertThat(json).doesNotContain("ownerMobile");
        assertThat(json).doesNotContain("contactNumber");
        assertThat(json).doesNotContain("alternate");
        assertThat(json).doesNotContain("additionalMobile");
        assertThat(json).doesNotContain("mobileNumber");
        assertThat(json).doesNotContain("9991110001");
        assertThat(json).contains("Sunrise PG");
        assertThat(json).contains("Wakad");
    }

    @Test
    void memberEnquiryResponse_doesNotLeakOwnerContact() throws Exception {
        SpaceEnquiryResponse response = SpaceEnquiryResponse.builder()
                .enquiryId(UUID.randomUUID())
                .spaceId(UUID.randomUUID())
                .spaceName("Sunrise PG")
                .requesterType(EnquiryRequesterType.MEMBER)
                .status(SpaceEnquiryStatus.SHARED)
                .requestedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(30))
                .detailsShared(true)
                .requesterEmail("ketan@example.com")
                .build();

        String json = mapper.writeValueAsString(response);
        assertThat(json).contains("detailsShared");
        assertThat(json).doesNotContain("9991110001");
        assertThat(json).doesNotContain("ownerContact");
        assertThat(json).doesNotContain("mobileNumber");
        assertThat(json).doesNotContain("alternateMobile");
        assertThat(json).doesNotContain("additionalMobile");
    }

    @Test
    void androidSharedMemberEnquiry_mayIncludeOwnerContactWhenAuthorized() throws Exception {
        SpaceEnquiryResponse response = SpaceEnquiryResponse.builder()
                .enquiryId(UUID.randomUUID())
                .spaceId(UUID.randomUUID())
                .spaceName("Sunrise PG")
                .requesterType(EnquiryRequesterType.MEMBER)
                .status(SpaceEnquiryStatus.SHARED)
                .requestedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(30))
                .detailsShared(true)
                .clientChannel(com.acomi.acomi_backend.inquirycredit.domain.model.InquiryClientChannel.ANDROID)
                .contactDelivery("IN_APP")
                .contactEmailSent(false)
                .requesterEmail("ketan@example.com")
                .ownerContact(OwnerContactResponse.builder()
                        .ownerName("Rahul")
                        .mobileNumber("9991110001")
                        .available(true)
                        .build())
                .build();

        String json = mapper.writeValueAsString(response);
        assertThat(json).contains("ownerContact");
        assertThat(json).contains("9991110001");
        assertThat(json).contains("IN_APP");
        assertThat(json).doesNotContain("Check your email");
    }

    @Test
    void requesterNotification_doesNotLeakOwnerContact() throws Exception {
        UserNotificationResponse response = UserNotificationResponse.builder()
                .notificationId(UUID.randomUUID())
                .spaceId(UUID.randomUUID())
                .enquiryId(UUID.randomUUID())
                .notificationType(com.acomi.acomi_backend.notification.domain.model.NotificationType.CONTACT_ENQUIRY_SHARED)
                .title("Contact details shared")
                .message("ACOMI has shared the contact details for Sunrise PG. Check your email for the contact information.")
                .actionRoute("MyEnquiries")
                .read(false)
                .createdAt(LocalDateTime.now())
                .build();

        String json = mapper.writeValueAsString(response);
        assertThat(json).contains("Sunrise PG");
        assertThat(json).contains("CONTACT_ENQUIRY_SHARED");
        assertThat(json).doesNotContain("ownerContact");
        assertThat(json).doesNotContain("mobileNumber");
        assertThat(json).doesNotContain("alternateMobile");
        assertThat(json).doesNotContain("additionalMobile");
        assertThat(json).doesNotContain("ownerEmail");
        assertThat(json).doesNotContain("9991110001");
        assertThat(json).doesNotContain("actorId");
        assertThat(json).doesNotContain("userId");
    }
}
