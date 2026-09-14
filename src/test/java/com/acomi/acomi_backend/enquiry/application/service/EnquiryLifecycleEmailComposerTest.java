package com.acomi.acomi_backend.enquiry.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.enquiry.domain.model.EnquiryRequesterType;
import com.acomi.acomi_backend.enquiry.domain.model.SpaceEnquiryStatus;
import com.acomi.acomi_backend.enquiry.infrastructure.persistence.entity.SpaceEnquiryEntity;
import com.acomi.acomi_backend.enquiry.infrastructure.persistence.repository.SpaceEnquiryRepository;
import com.acomi.acomi_backend.mail.application.dto.OutboundEmail;
import com.acomi.acomi_backend.mail.config.MailProperties;
import com.acomi.acomi_backend.mail.domain.model.EmailEventType;
import com.acomi.acomi_backend.mail.infrastructure.persistence.entity.EmailSendLogEntity;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EnquiryLifecycleEmailComposerTest {

    @Mock
    private SpaceEnquiryRepository enquiryRepository;

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private UserRepository userRepository;

    private EnquiryLifecycleEmailComposer composer;
    private UUID enquiryId;
    private UUID spaceId;
    private UUID requesterId;

    @BeforeEach
    void setUp() {
        MailProperties mailProperties = new MailProperties();
        mailProperties.setFrom("support@acomi.in");
        mailProperties.setFromName("ACOMI Support");
        mailProperties.setReplyTo("support@acomi.in");
        mailProperties.setSupportAddress("support@acomi.in");
        composer = new EnquiryLifecycleEmailComposer(
                enquiryRepository, spaceRepository, userRepository, mailProperties);
        enquiryId = UUID.randomUUID();
        spaceId = UUID.randomUUID();
        requesterId = UUID.randomUUID();
    }

    @Test
    void submittedRetryUsesRequesterEmailNotSupportMailbox() {
        when(enquiryRepository.findById(enquiryId)).thenReturn(Optional.of(enquiry()));
        OutboundEmail email = composer.compose(log(EmailEventType.ENQUIRY_SUBMITTED, "ketan@example.com")).orElseThrow();
        assertThat(email.to()).isEqualTo("ketan@example.com");
        assertThat(email.plainBody()).doesNotContain("9991110001");
        assertThat(email.from()).isEqualTo("support@acomi.in");
        assertThat(email.replyTo()).isEqualTo("support@acomi.in");
    }

    @Test
    void supportRetryNeverSendsToRequester() {
        when(enquiryRepository.findById(enquiryId)).thenReturn(Optional.of(enquiry()));
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space()));
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester()));

        OutboundEmail email =
                composer.compose(log(EmailEventType.ENQUIRY_SUBMITTED_SUPPORT, "support@acomi.in")).orElseThrow();

        assertThat(email.to()).isEqualTo("support@acomi.in");
        assertThat(email.plainBody()).contains("ketan@example.com");
        assertThat(email.plainBody()).contains("9876500001");
        assertThat(email.plainBody()).doesNotContain("9991110001");
        assertThat(email.plainBody()).doesNotContain("Owner contact");
    }

    private EmailSendLogEntity log(EmailEventType type, String recipient) {
        EmailSendLogEntity entity = EmailSendLogEntity.builder()
                .eventType(type)
                .recipientEmail(recipient)
                .fromEmail("support@acomi.in")
                .replyTo("support@acomi.in")
                .subject("test")
                .relatedEntityType("SPACE_ENQUIRY")
                .relatedEntityId(enquiryId)
                .idempotencyKey(type.name() + ":" + enquiryId)
                .build();
        return entity;
    }

    private SpaceEnquiryEntity enquiry() {
        SpaceEnquiryEntity entity = SpaceEnquiryEntity.builder()
                .spaceId(spaceId)
                .spaceNameSnapshot("Orchid Stay PG")
                .requesterUserId(requesterId)
                .requesterNameSnapshot("Ketan")
                .requesterEmail("ketan@example.com")
                .requesterType(EnquiryRequesterType.MEMBER)
                .status(SpaceEnquiryStatus.PENDING)
                .requestedAt(LocalDateTime.of(2026, 9, 9, 11, 30))
                .expiresAt(LocalDateTime.of(2026, 10, 9, 11, 30))
                .build();
        entity.setId(enquiryId);
        return entity;
    }

    private SpaceEntity space() {
        SpaceEntity entity = SpaceEntity.builder().name("Orchid Stay PG").type(SpaceType.PG).build();
        entity.setId(spaceId);
        return entity;
    }

    private UserEntity requester() {
        UserEntity entity = UserEntity.builder()
                .fullName("Ketan")
                .email("ketan@example.com")
                .mobileNumber("9876500001")
                .build();
        entity.setId(requesterId);
        return entity;
    }
}
