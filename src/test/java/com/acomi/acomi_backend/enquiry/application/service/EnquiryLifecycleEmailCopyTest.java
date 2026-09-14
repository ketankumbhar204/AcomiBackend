package com.acomi.acomi_backend.enquiry.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnquiryLifecycleEmailCopyTest {

    @Test
    void submittedEmailIsRequesterSafeAndStripsHeaderInjection() {
        String subject = EnquiryLifecycleEmailCopy.submittedSubject("Sunrise PG\r\nBcc: attacker@example.com");
        String body = EnquiryLifecycleEmailCopy.submittedBody("Ketan", "Sunrise PG");

        assertThat(subject).doesNotContain("\r").doesNotContain("\n");
        assertThat(subject).contains("Sunrise PG");
        assertThat(body).contains("Ketan").contains("Sunrise PG");
        assertThat(body).doesNotContain("9991110001");
        assertThat(body).doesNotContain("Owner contact");
        assertThat(body).doesNotContain("<html");
        assertThat(body).doesNotContain("admin team");
    }

    @Test
    void supportAlertDistinguishesAutomaticShareWithoutOwnerContact() {
        UUID enquiryId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String autoBody = EnquiryLifecycleEmailCopy.supportBody(
                "Orchid Stay PG",
                SpaceType.PG,
                "Ketan",
                "ketan@example.com",
                "9876500001",
                enquiryId,
                LocalDateTime.of(2026, 9, 9, 11, 30),
                true);

        assertThat(autoBody).contains("shared automatically");
        assertThat(autoBody).doesNotContain("Please review the enquiry");
        assertThat(autoBody).doesNotContain("9991110001");
        assertThat(autoBody).doesNotContain("Owner contact");
    }

    @Test
    void rejectedEmailOmitsAdminReasonAndOwnerContact() {
        String body = EnquiryLifecycleEmailCopy.rejectedBody("Ketan", "Orchid Stay PG");
        assertThat(body).contains("Orchid Stay PG");
        assertThat(body).doesNotContain("Not a fit");
        assertThat(body).doesNotContain("9991110001");
        assertThat(body).doesNotContain("Owner contact");
    }

    @Test
    void supportAlertContainsOperationalFieldsButNotOwnerContact() {
        UUID enquiryId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String body = EnquiryLifecycleEmailCopy.supportBody(
                "Orchid Stay PG",
                SpaceType.PG,
                "Ketan",
                "ketan@example.com",
                "9876500001",
                enquiryId,
                LocalDateTime.of(2026, 9, 9, 11, 30));

        assertThat(body).contains("Orchid Stay PG");
        assertThat(body).contains("PG");
        assertThat(body).contains("Ketan");
        assertThat(body).contains("ketan@example.com");
        assertThat(body).contains("9876500001");
        assertThat(body).contains(enquiryId.toString());
        assertThat(body).doesNotContain("9991110001");
        assertThat(body).doesNotContain("owner@example.com");
        assertThat(body).doesNotContain("Owner contact");
        assertThat(EnquiryLifecycleEmailCopy.supportSubject("Orchid Stay PG\nBcc: x"))
                .doesNotContain("\n");
    }
}
