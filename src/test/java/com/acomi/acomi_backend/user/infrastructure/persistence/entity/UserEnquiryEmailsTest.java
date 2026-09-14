package com.acomi.acomi_backend.user.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserEnquiryEmailsTest {

    @Test
    void rememberEnquiryEmailPrependsAndDedupesWithoutChangingProfileEmail() {
        UserEntity user = UserEntity.builder()
                .fullName("Ketan")
                .mobileNumber("9876500001")
                .email("profile@example.com")
                .build();

        user.rememberEnquiryEmail(" One@Example.com ");
        user.rememberEnquiryEmail("two@example.com");
        user.rememberEnquiryEmail("one@example.com");

        assertThat(user.getEmail()).isEqualTo("profile@example.com");
        assertThat(user.getEnquiryEmails()).containsExactly("one@example.com", "two@example.com");
        assertThat(user.enquiryEmailOptions())
                .containsExactly("one@example.com", "two@example.com", "profile@example.com");
    }

    @Test
    void rememberEnquiryEmailCapsAtTen() {
        UserEntity user = UserEntity.builder().fullName("Ketan").mobileNumber("9876500001").build();
        for (int i = 0; i < 12; i++) {
            user.rememberEnquiryEmail("user" + i + "@example.com");
        }
        assertThat(user.getEnquiryEmails()).hasSize(UserEntity.MAX_ENQUIRY_EMAILS);
        assertThat(user.getEnquiryEmails().get(0)).isEqualTo("user11@example.com");
        assertThat(user.getEnquiryEmails()).doesNotContain("user0@example.com");
        assertThat(user.getEmail()).isEqualTo("user0@example.com");
    }

    @Test
    void rememberEnquiryEmailFillsBlankProfileEmailOnce() {
        UserEntity user = UserEntity.builder().fullName("Ketan").mobileNumber("9876500001").build();

        user.rememberEnquiryEmail("first@example.com");
        user.rememberEnquiryEmail("second@example.com");

        assertThat(user.getEmail()).isEqualTo("first@example.com");
        assertThat(user.getEnquiryEmails()).containsExactly("second@example.com", "first@example.com");
    }
}
