package com.acomi.acomi_backend.enquiry.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.enquiry.api.dto.request.CreateSpaceEnquiryRequest;
import com.acomi.acomi_backend.inquirycredit.domain.model.InquiryAccessGrant;
import org.mockito.Mockito;
import com.acomi.acomi_backend.enquiry.api.dto.response.AdminSpaceEnquiryDetailResponse;
import com.acomi.acomi_backend.enquiry.api.dto.response.OwnerContactResponse;
import com.acomi.acomi_backend.enquiry.api.dto.response.SpaceEnquiryResponse;
import com.acomi.acomi_backend.enquiry.domain.model.EnquiryRequesterType;
import com.acomi.acomi_backend.enquiry.domain.model.SpaceEnquiryStatus;
import com.acomi.acomi_backend.enquiry.domain.policy.EnquiryAutoShareDecision;
import com.acomi.acomi_backend.enquiry.domain.policy.EnquiryAutoSharePolicy;
import com.acomi.acomi_backend.enquiry.domain.policy.EnquiryAutoShareReason;
import com.acomi.acomi_backend.enquiry.infrastructure.persistence.entity.SpaceEnquiryDeliveryEntity;
import com.acomi.acomi_backend.enquiry.infrastructure.persistence.entity.SpaceEnquiryEntity;
import com.acomi.acomi_backend.enquiry.infrastructure.persistence.repository.SpaceEnquiryDeliveryRepository;
import com.acomi.acomi_backend.enquiry.infrastructure.persistence.repository.SpaceEnquiryRepository;
import com.acomi.acomi_backend.inquirycredit.domain.model.InquiryClientChannel;
import com.acomi.acomi_backend.mail.application.dto.SendEmailCommand;
import com.acomi.acomi_backend.mail.application.service.EmailService;
import com.acomi.acomi_backend.mail.application.support.EmailIdempotencyKeys;
import com.acomi.acomi_backend.mail.config.MailProperties;
import com.acomi.acomi_backend.mail.domain.model.EmailEventType;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.notification.application.port.in.PublishNotificationCommand;
import com.acomi.acomi_backend.notification.application.service.NotificationService;
import com.acomi.acomi_backend.notification.domain.model.NotificationType;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.acomi.acomi_backend.user.domain.model.SystemRole;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class SpaceEnquiryServiceTest {

    @Mock
    private SpaceEnquiryRepository enquiryRepository;

    @Mock
    private SpaceEnquiryDeliveryRepository deliveryRepository;

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PropertyRegistrationRepository propertyRegistrationRepository;

    @Mock
    private MessRegistrationRepository messRegistrationRepository;

    @Mock
    private OwnerContactResolver ownerContactResolver;

    @Mock
    private EnquiryListingDetailsResolver listingDetailsResolver;

    @Mock
    private EnquiryAutoSharePolicy enquiryAutoSharePolicy;

    @Mock
    private EmailService emailService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private com.acomi.acomi_backend.inquirycredit.application.service.InquiryAccessService inquiryAccessService;

    private MailProperties mailProperties;
    private SpaceEnquiryService service;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-08T06:00:00Z"), ZoneId.of("Asia/Kolkata"));

    private UUID memberId;
    private UUID ownerAId;
    private UUID spaceAId;
    private UUID spaceBId;
    private UUID adminId;
    private UserEntity member;
    private UserEntity ownerA;
    private UserEntity admin;
    private SpaceEntity spaceA;
    private SpaceEntity spaceB;

    @BeforeEach
    void setUp() {
        mailProperties = new MailProperties();
        mailProperties.setFrom("support@acomi.in");
        mailProperties.setFromName("ACOMI Support");
        mailProperties.setReplyTo("support@acomi.in");
        mailProperties.setSupportAddress("support@acomi.in");
        service = new SpaceEnquiryService(
                enquiryRepository,
                deliveryRepository,
                spaceRepository,
                userRepository,
                propertyRegistrationRepository,
                messRegistrationRepository,
                ownerContactResolver,
                listingDetailsResolver,
                enquiryAutoSharePolicy,
                emailService,
                notificationService,
                mailProperties,
                clock,
                30,
                inquiryAccessService);
        // Lenient default: allow tests that create new enquiries to succeed without STRICT_STUBS failures
        Mockito.lenient().when(inquiryAccessService.authorizeNewEnquiry(any(), any()))
                .thenReturn(InquiryAccessGrant.FREE_WEB);
        Mockito.lenient()
                .when(listingDetailsResolver.resolve(any()))
                .thenReturn(EnquiryListingDetails.empty());
        Mockito.lenient()
                .when(deliveryRepository.findActiveAppDelivery(any(), any(), any()))
                .thenReturn(Optional.empty());
        Mockito.lenient()
                .when(deliveryRepository.findActiveEmailDelivery(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        Mockito.lenient()
                .when(deliveryRepository.findAppDeliveryForUpdate(any(), any(), any()))
                .thenReturn(Optional.empty());
        Mockito.lenient()
                .when(deliveryRepository.findEmailDeliveryForUpdate(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        Mockito.lenient()
                .when(deliveryRepository.existsBySpaceIdAndRequesterUserIdAndDeliveryChannel(any(), any(), any()))
                .thenReturn(false);
        Mockito.lenient()
                .when(deliveryRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> {
                    SpaceEnquiryDeliveryEntity entity = invocation.getArgument(0);
                    if (entity.getId() == null) {
                        entity.setId(UUID.randomUUID());
                    }
                    return entity;
                });
        Mockito.lenient()
                .when(deliveryRepository.save(any()))
                .thenAnswer(invocation -> {
                    SpaceEnquiryDeliveryEntity entity = invocation.getArgument(0);
                    if (entity.getId() == null) {
                        entity.setId(UUID.randomUUID());
                    }
                    return entity;
                });
        memberId = UUID.randomUUID();
        ownerAId = UUID.randomUUID();
        spaceAId = UUID.randomUUID();
        spaceBId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        member = user("Ketan", "9876500001", "ketan@example.com", memberId, SystemRole.USER);
        ownerA = user("Rahul", "9876500002", "rahul@example.com", ownerAId, SystemRole.USER);
        admin = user("Admin", "9000000001", "admin@acomi.in", adminId, SystemRole.ADMIN);
        spaceA = space("Sunrise PG", spaceAId, ownerA);
        spaceB = space("Orchid Stay PG", spaceBId, user("Other", "9876500003", null, UUID.randomUUID(), SystemRole.USER));
    }

    @Test
    void memberCanSubmitEnquiryAndAdminIsNotified() {
        stubCreateSuccess(member, spaceB, false);
        when(userRepository.findBySystemRoleAndIsActiveTrue(SystemRole.ADMIN)).thenReturn(List.of(admin));
        when(enquiryRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            SpaceEnquiryEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        SpaceEnquiryResponse response = service.create(memberId, spaceBId, new CreateSpaceEnquiryRequest());

        assertThat(response.getStatus()).isEqualTo(SpaceEnquiryStatus.PENDING);
        assertThat(response.getSpaceName()).isEqualTo("Orchid Stay PG");
        assertThat(response.getRequesterEmail()).isEqualTo("ketan@example.com");
        assertThat(response.isDetailsShared()).isFalse();
        assertThat(response.isReusedExisting()).isFalse();
        ArgumentCaptor<PublishNotificationCommand> captor = ArgumentCaptor.forClass(PublishNotificationCommand.class);
        verify(notificationService, times(2)).publish(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(PublishNotificationCommand::getNotificationType)
                .containsExactlyInAnyOrder(
                        NotificationType.CONTACT_ENQUIRY, NotificationType.CONTACT_ENQUIRY_SUBMITTED);
        PublishNotificationCommand adminNote = captor.getAllValues().stream()
                .filter(c -> c.getNotificationType() == NotificationType.CONTACT_ENQUIRY)
                .findFirst()
                .orElseThrow();
        assertThat(adminNote.getTitle()).isEqualTo("New contact enquiry");
        assertThat(adminNote.getMessage()).contains("Ketan").contains("Orchid Stay PG");
        assertThat(adminNote.getMessage()).doesNotContain("98765");
        assertThat(adminNote.getActionRoute()).isEqualTo("AdminEnquiryDetail");
        PublishNotificationCommand requesterNote = captor.getAllValues().stream()
                .filter(c -> c.getNotificationType() == NotificationType.CONTACT_ENQUIRY_SUBMITTED)
                .findFirst()
                .orElseThrow();
        assertThat(requesterNote.getUserId()).isEqualTo(memberId);
        assertThat(requesterNote.getMessage()).doesNotContain("98765");
        assertThat(requesterNote.getActionRoute()).isEqualTo("MyEnquiries");

        ArgumentCaptor<SendEmailCommand> mailCaptor = ArgumentCaptor.forClass(SendEmailCommand.class);
        verify(emailService, times(1)).send(mailCaptor.capture());
        SendEmailCommand support = mailOf(mailCaptor, EmailEventType.ENQUIRY_SUBMITTED_SUPPORT);
        assertThat(support.getRecipientEmail()).isEqualTo("support@acomi.in");
        assertThat(support.getRecipientUserId()).isNull();
        assertThat(support.getIdempotencyKey()).startsWith("ENQUIRY_SUBMITTED_SUPPORT:");
        assertThat(support.getPlainBody()).contains("Ketan");
        assertThat(support.getPlainBody()).contains("ketan@example.com");
        assertThat(support.getPlainBody()).contains("9876500001");
        assertThat(support.getPlainBody()).contains("Orchid Stay PG");
        assertThat(support.getPlainBody()).doesNotContain("9991110001");
        assertThat(support.getPlainBody()).doesNotContain("9876500003");
        assertThat(support.getPlainBody()).doesNotContain("owner@example.com");
        assertThat(support.getPlainBody()).doesNotContain("Owner contact");
        assertThat(support.getPlainBody()).contains("Please review the enquiry");
        assertThat(support.getPlainBody()).doesNotContain("shared automatically");
    }

    @Test
    void eligibleEnquiryIsAutomaticallySharedWithoutAdminNotification() {
        stubCreateSuccess(member, spaceB, false);
        when(enquiryAutoSharePolicy.evaluate(any(), any()))
                .thenReturn(EnquiryAutoShareDecision.allow(shareableContact()));
        when(enquiryRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            SpaceEnquiryEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            return entity;
        });
        when(enquiryRepository.save(any())).thenAnswer(invocation -> {
            SpaceEnquiryEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            return entity;
        });

        SpaceEnquiryResponse response = service.create(memberId, spaceBId, new CreateSpaceEnquiryRequest());

        assertThat(response.getStatus()).isEqualTo(SpaceEnquiryStatus.SHARED);
        assertThat(response.isDetailsShared()).isTrue();
        assertThat(response.getSharedAt()).isNotNull();
        assertThat(response.isContactEmailSent()).isFalse();
        ArgumentCaptor<PublishNotificationCommand> noteCaptor = ArgumentCaptor.forClass(PublishNotificationCommand.class);
        verify(notificationService, times(2)).publish(noteCaptor.capture());
        assertThat(noteCaptor.getAllValues())
                .extracting(PublishNotificationCommand::getNotificationType)
                .containsExactly(
                        NotificationType.CONTACT_ENQUIRY_SUBMITTED, NotificationType.CONTACT_ENQUIRY_SHARED);
        assertThat(noteCaptor.getAllValues())
                .extracting(PublishNotificationCommand::getNotificationType)
                .doesNotContain(NotificationType.CONTACT_ENQUIRY);
        noteCaptor.getAllValues().forEach(note -> {
            assertThat(note.getMessage()).doesNotContain("9991110001");
            assertThat(note.getMessage()).doesNotContain("owner@example.com");
            assertThat(note.getMessage()).doesNotContain("Check your email");
        });

        ArgumentCaptor<SendEmailCommand> mailCaptor = ArgumentCaptor.forClass(SendEmailCommand.class);
        verify(emailService, times(1)).send(mailCaptor.capture());
        SendEmailCommand support = mailOf(mailCaptor, EmailEventType.ENQUIRY_SUBMITTED_SUPPORT);
        assertThat(support.getRecipientEmail()).isEqualTo("support@acomi.in");
        assertThat(support.getPlainBody()).contains("shared automatically");
        assertThat(mailCaptor.getAllValues())
                .extracting(SendEmailCommand::getEventType)
                .doesNotContain(EmailEventType.ENQUIRY_SHARED);
        verify(userRepository, never()).findBySystemRoleAndIsActiveTrue(SystemRole.ADMIN);
    }

    @Test
    void autoSharedEnquiryCannotBeSharedAgainByAdmin() {
        SpaceEnquiryEntity alreadyShared = pendingEnquiry();
        alreadyShared.setStatus(SpaceEnquiryStatus.SHARED);
        alreadyShared.setSharedAt(LocalDateTime.now(clock));
        alreadyShared.setSharedByAdminId(null);
        when(enquiryRepository.findById(alreadyShared.getId())).thenReturn(Optional.of(alreadyShared));

        assertThatThrownBy(() -> service.share(alreadyShared.getId(), adminId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo("ENQUIRY_ALREADY_SHARED");
        verify(emailService, never()).send(any());
    }

    @Test
    void ownerCanEnquireAboutAnotherSpace() {
        stubCreateSuccess(ownerA, spaceB, true);
        when(userRepository.findBySystemRoleAndIsActiveTrue(SystemRole.ADMIN)).thenReturn(List.of());
        when(enquiryRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            SpaceEnquiryEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        SpaceEnquiryResponse response = service.create(ownerAId, spaceBId, new CreateSpaceEnquiryRequest());

        assertThat(response.getRequesterType()).isEqualTo(EnquiryRequesterType.OWNER);
        ArgumentCaptor<PublishNotificationCommand> captor = ArgumentCaptor.forClass(PublishNotificationCommand.class);
        verify(notificationService, times(1)).publish(captor.capture());
        assertThat(captor.getValue().getNotificationType()).isEqualTo(NotificationType.CONTACT_ENQUIRY_SUBMITTED);
        assertThat(captor.getValue().getUserId()).isEqualTo(ownerAId);
    }

    @Test
    void ownerCannotEnquireAboutOwnSpace() {
        when(userRepository.findByIdAndIsActiveTrue(ownerAId)).thenReturn(Optional.of(ownerA));
        when(spaceRepository.findByIdAndIsActiveTrueAndDiscoverableTrue(spaceAId)).thenReturn(Optional.of(spaceA));
        when(spaceRepository.existsByIdAndOwnerIdAndIsActiveTrue(spaceAId, ownerAId)).thenReturn(true);

        assertThatThrownBy(() -> service.create(ownerAId, spaceAId, new CreateSpaceEnquiryRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo("SELF_ENQUIRY_NOT_ALLOWED");
        verify(enquiryRepository, never()).save(any());
    }

    @Test
    void createRequiresEmailWhenProfileHasNone() {
        member.setEmail(null);
        when(userRepository.findByIdAndIsActiveTrue(memberId)).thenReturn(Optional.of(member));
        when(spaceRepository.findByIdAndIsActiveTrueAndDiscoverableTrue(spaceBId)).thenReturn(Optional.of(spaceB));
        when(spaceRepository.existsByIdAndOwnerIdAndIsActiveTrue(spaceBId, memberId)).thenReturn(false);

        assertThatThrownBy(() -> service.create(memberId, spaceBId, new CreateSpaceEnquiryRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo("REQUESTER_EMAIL_REQUIRED");
    }

    @Test
    void createFillsBlankProfileEmailFromEnquiry() {
        member.setEmail(null);
        CreateSpaceEnquiryRequest request = new CreateSpaceEnquiryRequest();
        request.setEmail("one-off@example.com");
        stubCreateSuccess(member, spaceB, false);
        when(userRepository.findBySystemRoleAndIsActiveTrue(SystemRole.ADMIN)).thenReturn(List.of());
        when(enquiryRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            SpaceEnquiryEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        SpaceEnquiryResponse response = service.create(memberId, spaceBId, request);

        assertThat(response.getRequesterEmail()).isEqualTo("one-off@example.com");
        assertThat(member.getEmail()).isEqualTo("one-off@example.com");
        assertThat(member.getEnquiryEmails()).containsExactly("one-off@example.com");
        ArgumentCaptor<SendEmailCommand> mailCaptor = ArgumentCaptor.forClass(SendEmailCommand.class);
        verify(emailService, times(1)).send(mailCaptor.capture());
        assertThat(mailOf(mailCaptor, EmailEventType.ENQUIRY_SUBMITTED_SUPPORT).getRecipientEmail())
                .isEqualTo("support@acomi.in");
    }

    @Test
    void existingPendingEnquiryDoesNotCreateDuplicateEmails() {
        SpaceEnquiryEntity pending = pendingEnquiry();
        when(userRepository.findByIdAndIsActiveTrue(memberId)).thenReturn(Optional.of(member));
        when(spaceRepository.findByIdAndIsActiveTrueAndDiscoverableTrue(spaceBId)).thenReturn(Optional.of(spaceB));
        when(spaceRepository.existsByIdAndOwnerIdAndIsActiveTrue(spaceBId, memberId)).thenReturn(false);
        when(enquiryRepository.findBySpaceIdAndRequesterUserIdAndStatus(
                        spaceBId, memberId, SpaceEnquiryStatus.PENDING))
                .thenReturn(Optional.of(pending));

        SpaceEnquiryResponse response = service.create(memberId, spaceBId, new CreateSpaceEnquiryRequest());

        assertThat(response.getEnquiryId()).isEqualTo(pending.getId());
        assertThat(response.isReusedExisting()).isTrue();
        verify(enquiryRepository, never()).save(any());
        verify(emailService, never()).send(any());
        verify(notificationService, never()).publish(any());
    }

    @Test
    void concurrentCreateRecoversExistingPendingWithoutSecondEmails() {
        SpaceEnquiryEntity pending = pendingEnquiry();
        when(userRepository.findByIdAndIsActiveTrue(memberId)).thenReturn(Optional.of(member));
        when(spaceRepository.findByIdAndIsActiveTrueAndDiscoverableTrue(spaceBId)).thenReturn(Optional.of(spaceB));
        when(spaceRepository.existsByIdAndOwnerIdAndIsActiveTrue(spaceBId, memberId)).thenReturn(false);
        when(enquiryRepository.findBySpaceIdAndRequesterUserIdAndStatus(
                        spaceBId, memberId, SpaceEnquiryStatus.PENDING))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(pending));
        when(enquiryRepository.findBySpaceIdAndRequesterUserIdAndStatus(
                        spaceBId, memberId, SpaceEnquiryStatus.SHARED))
                .thenReturn(Optional.empty());
        when(enquiryRepository.saveAndFlush(any(SpaceEnquiryEntity.class)))
                .thenThrow(new DataIntegrityViolationException("uq_space_enquiries_active_requester_space"));

        SpaceEnquiryResponse response = service.create(memberId, spaceBId, new CreateSpaceEnquiryRequest());

        assertThat(response.getEnquiryId()).isEqualTo(pending.getId());
        assertThat(response.getStatus()).isEqualTo(SpaceEnquiryStatus.PENDING);
        assertThat(response.isReusedExisting()).isTrue();
        verify(emailService, never()).send(any());
        verify(notificationService, never()).publish(any());
    }

    @Test
    void existingSharedEnquiryIsReusedWithoutSecondEmailsOrNotifications() {
        SpaceEnquiryEntity shared = pendingEnquiry();
        shared.setStatus(SpaceEnquiryStatus.SHARED);
        shared.setSharedAt(LocalDateTime.now(clock));
        when(userRepository.findByIdAndIsActiveTrue(memberId)).thenReturn(Optional.of(member));
        when(spaceRepository.findByIdAndIsActiveTrueAndDiscoverableTrue(spaceBId)).thenReturn(Optional.of(spaceB));
        when(spaceRepository.existsByIdAndOwnerIdAndIsActiveTrue(spaceBId, memberId)).thenReturn(false);
        when(enquiryRepository.findBySpaceIdAndRequesterUserIdAndStatus(
                        spaceBId, memberId, SpaceEnquiryStatus.PENDING))
                .thenReturn(Optional.empty());
        when(enquiryRepository.findBySpaceIdAndRequesterUserIdAndStatus(
                        spaceBId, memberId, SpaceEnquiryStatus.SHARED))
                .thenReturn(Optional.of(shared));

        SpaceEnquiryResponse response = service.create(memberId, spaceBId, new CreateSpaceEnquiryRequest());

        assertThat(response.getEnquiryId()).isEqualTo(shared.getId());
        assertThat(response.getStatus()).isEqualTo(SpaceEnquiryStatus.SHARED);
        assertThat(response.isDetailsShared()).isTrue();
        assertThat(response.getSharedAt()).isEqualTo(shared.getSharedAt());
        assertThat(response.isReusedExisting()).isTrue();
        verify(enquiryRepository, never()).saveAndFlush(any());
        verify(enquiryRepository, never()).save(any());
        verify(emailService, never()).send(any());
        verify(notificationService, never()).publish(any());
        verify(enquiryAutoSharePolicy, never()).evaluate(any(), any());
    }

    @Test
    void concurrentCreateAfterShareRecoversSharedWithoutSecondEmails() {
        SpaceEnquiryEntity shared = pendingEnquiry();
        shared.setStatus(SpaceEnquiryStatus.SHARED);
        shared.setSharedAt(LocalDateTime.now(clock));
        when(userRepository.findByIdAndIsActiveTrue(memberId)).thenReturn(Optional.of(member));
        when(spaceRepository.findByIdAndIsActiveTrueAndDiscoverableTrue(spaceBId)).thenReturn(Optional.of(spaceB));
        when(spaceRepository.existsByIdAndOwnerIdAndIsActiveTrue(spaceBId, memberId)).thenReturn(false);
        when(enquiryRepository.findBySpaceIdAndRequesterUserIdAndStatus(
                        spaceBId, memberId, SpaceEnquiryStatus.PENDING))
                .thenReturn(Optional.empty());
        when(enquiryRepository.findBySpaceIdAndRequesterUserIdAndStatus(
                        spaceBId, memberId, SpaceEnquiryStatus.SHARED))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(shared));
        when(enquiryRepository.saveAndFlush(any(SpaceEnquiryEntity.class)))
                .thenThrow(new DataIntegrityViolationException("uq_space_enquiries_active_requester_space"));

        SpaceEnquiryResponse response = service.create(memberId, spaceBId, new CreateSpaceEnquiryRequest());

        assertThat(response.getEnquiryId()).isEqualTo(shared.getId());
        assertThat(response.getStatus()).isEqualTo(SpaceEnquiryStatus.SHARED);
        assertThat(response.isReusedExisting()).isTrue();
        verify(emailService, never()).send(any());
        verify(notificationService, never()).publish(any());
    }

    @Test
    void rejectedEnquiryAllowsANewEnquiry() {
        stubCreateSuccess(member, spaceB, false);
        when(userRepository.findBySystemRoleAndIsActiveTrue(SystemRole.ADMIN)).thenReturn(List.of(admin));
        when(enquiryRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            SpaceEnquiryEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        SpaceEnquiryResponse response = service.create(memberId, spaceBId, new CreateSpaceEnquiryRequest());

        assertThat(response.getStatus()).isEqualTo(SpaceEnquiryStatus.PENDING);
        assertThat(response.getEnquiryId()).isNotNull();
        verify(enquiryRepository).saveAndFlush(any(SpaceEnquiryEntity.class));
        ArgumentCaptor<SendEmailCommand> mailCaptor = ArgumentCaptor.forClass(SendEmailCommand.class);
        verify(emailService, times(1)).send(mailCaptor.capture());
        assertThat(mailCaptor.getAllValues())
                .extracting(SendEmailCommand::getEventType)
                .containsExactly(EmailEventType.ENQUIRY_SUBMITTED_SUPPORT);
    }

    @Test
    void expiredEnquiryAllowsANewEnquiryWithoutMutatingHistory() {
        SpaceEnquiryEntity expired = pendingEnquiry();
        expired.setStatus(SpaceEnquiryStatus.EXPIRED);
        stubCreateSuccess(member, spaceB, false);
        when(userRepository.findBySystemRoleAndIsActiveTrue(SystemRole.ADMIN)).thenReturn(List.of());
        when(enquiryRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            SpaceEnquiryEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        SpaceEnquiryResponse response = service.create(memberId, spaceBId, new CreateSpaceEnquiryRequest());

        assertThat(response.getStatus()).isEqualTo(SpaceEnquiryStatus.PENDING);
        assertThat(response.getEnquiryId()).isNotEqualTo(expired.getId());
        assertThat(expired.getStatus()).isEqualTo(SpaceEnquiryStatus.EXPIRED);
        verify(enquiryRepository).saveAndFlush(any(SpaceEnquiryEntity.class));
    }

    @Test
    void sameRequesterCanEnquireAboutADifferentSpace() {
        stubCreateSuccess(member, spaceA, false);
        when(userRepository.findBySystemRoleAndIsActiveTrue(SystemRole.ADMIN)).thenReturn(List.of());
        when(enquiryRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            SpaceEnquiryEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        SpaceEnquiryResponse response = service.create(memberId, spaceAId, new CreateSpaceEnquiryRequest());

        assertThat(response.getSpaceId()).isEqualTo(spaceAId);
        verify(enquiryRepository).saveAndFlush(any(SpaceEnquiryEntity.class));
    }

    @Test
    void differentRequesterCanEnquireAboutTheSameSpace() {
        UUID otherId = UUID.randomUUID();
        UserEntity other = user("Neha", "9876500099", "neha@example.com", otherId, SystemRole.USER);
        stubCreateSuccess(other, spaceB, false);
        when(userRepository.findBySystemRoleAndIsActiveTrue(SystemRole.ADMIN)).thenReturn(List.of());
        when(enquiryRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            SpaceEnquiryEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        SpaceEnquiryResponse response = service.create(otherId, spaceBId, new CreateSpaceEnquiryRequest());

        assertThat(response.getSpaceId()).isEqualTo(spaceBId);
        verify(enquiryRepository).saveAndFlush(any(SpaceEnquiryEntity.class));
    }

    @Test
    void nonDiscoverableSpaceCannotReuseExistingSharedEnquiry() {
        when(userRepository.findByIdAndIsActiveTrue(memberId)).thenReturn(Optional.of(member));
        when(spaceRepository.findByIdAndIsActiveTrueAndDiscoverableTrue(spaceBId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(memberId, spaceBId, new CreateSpaceEnquiryRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(enquiryRepository);
        verify(emailService, never()).send(any());
    }

    @Test
    void enquiryResponseDoesNotIncludeOwnerContact() {
        SpaceEnquiryEntity pending = pendingEnquiry();
        when(enquiryRepository.findByIdAndRequesterUserId(pending.getId(), memberId))
                .thenReturn(Optional.of(pending));

        SpaceEnquiryResponse response = service.getMine(memberId, pending.getId());

        assertThat(response.getStatus()).isEqualTo(SpaceEnquiryStatus.PENDING);
        assertThat(response.isDetailsShared()).isFalse();
    }

    @Test
    void listMineIncludesPlaceSummaryWithoutOwnerContact() {
        SpaceEnquiryEntity pending = pendingEnquiry();
        EnquiryListingDetails listing = new EnquiryListingDetails(
                null,
                "Kothrud",
                "Pune",
                "Maharashtra",
                "411038",
                "Kothrud, Pune, Maharashtra, 411038",
                null,
                null,
                "Mixed",
                null,
                null,
                null,
                null,
                null,
                null,
                "1, 2 & 3 Sharing",
                "Power Backup");
        when(enquiryRepository.findByRequesterUserIdOrderByRequestedAtDesc(eq(memberId), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(pending)));
        when(spaceRepository.findAllById(any())).thenReturn(List.of(spaceB));
        when(listingDetailsResolver.resolve(spaceB)).thenReturn(listing);

        var page = service.listMine(memberId, org.springframework.data.domain.PageRequest.of(0, 20));
        SpaceEnquiryResponse response = page.getContent().get(0);

        assertThat(response.getSpaceName()).isEqualTo("Orchid Stay PG");
        assertThat(response.getSpaceType()).isEqualTo(SpaceType.PG);
        assertThat(response.getLocationLabel()).isEqualTo("Kothrud, Pune");
        assertThat(response.getSharingNotes()).isEqualTo("1, 2 & 3 Sharing");
        assertThat(response.getAmenityLabels()).containsExactly("Power Backup");
        assertThat(response.getFoodIncludedInRent()).isFalse();
    }

    @Test
    void requesterCannotLoadAnotherUsersEnquiry() {
        SpaceEnquiryEntity pending = pendingEnquiry();
        when(enquiryRepository.findByIdAndRequesterUserId(pending.getId(), ownerAId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMine(ownerAId, pending.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void adminCanViewOwnerContactFromRegistration() {
        SpaceEnquiryEntity pending = pendingEnquiry();
        OwnerContactResponse contact = OwnerContactResponse.builder()
                .ownerName("Owner A")
                .mobileNumber("9991110001")
                .alternateMobileNumber("9991110002")
                .additionalMobileNumber("9991110003")
                .email("owner@example.com")
                .available(true)
                .build();
        when(enquiryRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
        when(spaceRepository.findById(spaceBId)).thenReturn(Optional.of(spaceB));
        when(ownerContactResolver.resolveOrEmpty(spaceB)).thenReturn(contact);

        AdminSpaceEnquiryDetailResponse detail = service.getForAdmin(pending.getId());

        assertThat(detail.getOwnerContact().getMobileNumber()).isEqualTo("9991110001");
        assertThat(detail.getOwnerContact().getAdditionalMobileNumber()).isEqualTo("9991110003");
        assertThat(detail.isAutomaticallyShared()).isFalse();
    }

    @Test
    void adminDetailMarksAutomaticallySharedWhenNoAdminActor() {
        SpaceEnquiryEntity autoShared = pendingEnquiry();
        autoShared.setStatus(SpaceEnquiryStatus.SHARED);
        autoShared.setSharedAt(LocalDateTime.now(clock));
        autoShared.setSharedByAdminId(null);
        when(enquiryRepository.findById(autoShared.getId())).thenReturn(Optional.of(autoShared));
        when(spaceRepository.findById(spaceBId)).thenReturn(Optional.of(spaceB));
        when(ownerContactResolver.resolveOrEmpty(spaceB)).thenReturn(shareableContact());

        AdminSpaceEnquiryDetailResponse detail = service.getForAdmin(autoShared.getId());

        assertThat(detail.getStatus()).isEqualTo(SpaceEnquiryStatus.SHARED);
        assertThat(detail.getSharedByAdminId()).isNull();
        assertThat(detail.isAutomaticallyShared()).isTrue();
    }

    @Test
    void shareMarksReadyWithoutEmail_webDeliverContactByEmailSendsOnce() {
        SpaceEnquiryEntity pending = pendingEnquiry();
        OwnerContactResponse contact = shareableContact();
        when(enquiryRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
        when(userRepository.findByIdAndIsActiveTrue(memberId)).thenReturn(Optional.of(member));
        when(spaceRepository.findByIdAndIsActiveTrue(spaceBId)).thenReturn(Optional.of(spaceB));
        when(ownerContactResolver.resolve(spaceB)).thenReturn(contact);
        when(ownerContactResolver.hasShareableContact(contact)).thenReturn(true);

        AdminSpaceEnquiryDetailResponse shared = service.share(pending.getId(), adminId);

        assertThat(shared.getStatus()).isEqualTo(SpaceEnquiryStatus.SHARED);
        assertThat(shared.getSharedByAdminId()).isEqualTo(adminId);
        assertThat(pending.getContactEmailSentAt()).isNull();
        verify(emailService, never()).send(any());

        ArgumentCaptor<PublishNotificationCommand> captor = ArgumentCaptor.forClass(PublishNotificationCommand.class);
        verify(notificationService).publish(captor.capture());
        assertThat(captor.getValue().getNotificationType()).isEqualTo(NotificationType.CONTACT_ENQUIRY_SHARED);
        assertThat(captor.getValue().getMessage())
                .contains("are ready")
                .doesNotContain("Check your email")
                .doesNotContain("9991110001");

        when(enquiryRepository.findByIdAndRequesterUserId(pending.getId(), memberId))
                .thenReturn(Optional.of(pending));

        SpaceEnquiryResponse emailed =
                service.deliverContactByEmail(memberId, pending.getId(), "alt@example.com");

        assertThat(emailed.isContactEmailSent()).isTrue();
        assertThat(emailed.getContactDelivery()).isEqualTo("EMAIL");
        assertThat(emailed.getRequesterEmail()).isEqualTo("alt@example.com");
        ArgumentCaptor<SendEmailCommand> mailCaptor = ArgumentCaptor.forClass(SendEmailCommand.class);
        verify(emailService, times(1)).send(mailCaptor.capture());
        assertThat(mailCaptor.getValue().getEventType()).isEqualTo(EmailEventType.ENQUIRY_SHARED);
        assertThat(mailCaptor.getValue().getRecipientEmail()).isEqualTo("alt@example.com");
        assertThat(mailCaptor.getValue().getPlainBody()).contains("+919991110001");
        assertThat(mailCaptor.getValue().getIdempotencyKey())
                .isEqualTo(EmailIdempotencyKeys.enquirySharedTo(pending.getId(), "alt@example.com"));
    }

    @Test
    void androidEnquiryAutoShareDeliversInAppWithoutOwnerContactEmail() {
        stubCreateSuccess(member, spaceB, false);
        when(enquiryAutoSharePolicy.evaluate(any(), any()))
                .thenReturn(EnquiryAutoShareDecision.allow(shareableContact()));
        when(ownerContactResolver.resolveOrEmpty(any())).thenReturn(shareableContact());
        when(enquiryRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            SpaceEnquiryEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            when(enquiryRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
            return entity;
        });
        when(enquiryRepository.save(any())).thenAnswer(invocation -> {
            SpaceEnquiryEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            when(enquiryRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
            return entity;
        });

        SpaceEnquiryResponse response = service.create(
                memberId, spaceBId, new CreateSpaceEnquiryRequest(), InquiryClientChannel.ANDROID);

        assertThat(response.getStatus()).isEqualTo(SpaceEnquiryStatus.SHARED);
        assertThat(response.getClientChannel()).isEqualTo(InquiryClientChannel.ANDROID);
        assertThat(response.getContactDelivery()).isEqualTo("IN_APP");
        assertThat(response.isContactEmailSent()).isFalse();
        assertThat(response.getContactEmailSentAt()).isNull();
        assertThat(response.getOwnerContact()).isNotNull();
        assertThat(response.getOwnerContact().getMobileNumber()).isEqualTo("9991110001");

        ArgumentCaptor<SpaceEnquiryEntity> saved = ArgumentCaptor.forClass(SpaceEnquiryEntity.class);
        verify(enquiryRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getClientChannel()).isEqualTo(InquiryClientChannel.ANDROID);

        ArgumentCaptor<SendEmailCommand> mailCaptor = ArgumentCaptor.forClass(SendEmailCommand.class);
        verify(emailService, times(1)).send(mailCaptor.capture());
        assertThat(mailCaptor.getAllValues())
                .extracting(SendEmailCommand::getEventType)
                .containsExactly(EmailEventType.ENQUIRY_SUBMITTED_SUPPORT)
                .doesNotContain(EmailEventType.ENQUIRY_SHARED);

        ArgumentCaptor<PublishNotificationCommand> noteCaptor =
                ArgumentCaptor.forClass(PublishNotificationCommand.class);
        verify(notificationService, times(2)).publish(noteCaptor.capture());
        PublishNotificationCommand sharedNote = noteCaptor.getAllValues().stream()
                .filter(n -> n.getNotificationType() == NotificationType.CONTACT_ENQUIRY_SHARED)
                .findFirst()
                .orElseThrow();
        assertThat(sharedNote.getMessage())
                .contains("My Enquiries")
                .doesNotContain("Check your email")
                .doesNotContain("9991110001");
    }

    @Test
    void androidAdminShareSkipsOwnerContactEmailAndNotifiesInApp() {
        SpaceEnquiryEntity pending = pendingEnquiry();
        pending.setClientChannel(InquiryClientChannel.ANDROID);
        OwnerContactResponse contact = shareableContact();
        when(enquiryRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
        when(userRepository.findByIdAndIsActiveTrue(memberId)).thenReturn(Optional.of(member));
        when(spaceRepository.findByIdAndIsActiveTrue(spaceBId)).thenReturn(Optional.of(spaceB));
        when(ownerContactResolver.resolve(spaceB)).thenReturn(contact);
        when(ownerContactResolver.hasShareableContact(contact)).thenReturn(true);

        AdminSpaceEnquiryDetailResponse shared = service.share(pending.getId(), adminId);

        assertThat(shared.getStatus()).isEqualTo(SpaceEnquiryStatus.SHARED);
        assertThat(pending.getContactEmailSentAt()).isNull();
        verify(emailService, never()).send(any());

        ArgumentCaptor<PublishNotificationCommand> captor = ArgumentCaptor.forClass(PublishNotificationCommand.class);
        verify(notificationService).publish(captor.capture());
        assertThat(captor.getValue().getMessage())
                .contains("Open My Enquiries")
                .doesNotContain("Check your email");
    }

    @Test
    void webMemberEnquiryDoesNotExposeOwnerContactAfterShare() {
        SpaceEnquiryEntity shared = pendingEnquiry();
        shared.setClientChannel(InquiryClientChannel.WEB);
        shared.setStatus(SpaceEnquiryStatus.SHARED);
        shared.setSharedAt(LocalDateTime.now(clock));
        when(enquiryRepository.findByIdAndRequesterUserId(shared.getId(), memberId))
                .thenReturn(Optional.of(shared));
        when(spaceRepository.findById(spaceBId)).thenReturn(Optional.of(spaceB));

        SpaceEnquiryResponse response = service.getMine(memberId, shared.getId());

        assertThat(response.getContactDelivery()).isNull();
        assertThat(response.isContactEmailSent()).isFalse();
        assertThat(response.getOwnerContact()).isNull();
        verify(ownerContactResolver, never()).resolveOrEmpty(any());
    }

    @Test
    void duplicateShareDoesNotSendAnotherEmail() {
        SpaceEnquiryEntity alreadyShared = pendingEnquiry();
        alreadyShared.setStatus(SpaceEnquiryStatus.SHARED);
        alreadyShared.setSharedAt(LocalDateTime.now(clock));
        alreadyShared.setSharedByAdminId(adminId);
        when(enquiryRepository.findById(alreadyShared.getId())).thenReturn(Optional.of(alreadyShared));

        assertThatThrownBy(() -> service.share(alreadyShared.getId(), adminId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo("ENQUIRY_ALREADY_SHARED");
        verify(emailService, never()).send(any());
        verify(notificationService, never()).publish(any());
    }

    @Test
    void expiredEnquiryCannotBeSharedAndRemainsInHistory() {
        SpaceEnquiryEntity pending = pendingEnquiry();
        pending.setExpiresAt(LocalDateTime.now(clock).minusDays(1));
        when(enquiryRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.share(pending.getId(), adminId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo("ENQUIRY_NOT_PENDING");
        assertThat(pending.getStatus()).isEqualTo(SpaceEnquiryStatus.EXPIRED);
        verify(emailService, never()).send(any());
        verify(enquiryRepository).save(pending);
        ArgumentCaptor<PublishNotificationCommand> captor = ArgumentCaptor.forClass(PublishNotificationCommand.class);
        verify(notificationService).publish(captor.capture());
        assertThat(captor.getValue().getNotificationType()).isEqualTo(NotificationType.CONTACT_ENQUIRY_EXPIRED);
        assertThat(captor.getValue().getMessage()).doesNotContain("9991110001");
    }

    @Test
    void expireDueNotifiesRequesterAndDoesNotEmail() {
        SpaceEnquiryEntity pending = pendingEnquiry();
        pending.setExpiresAt(LocalDateTime.now(clock).minusDays(1));
        when(enquiryRepository.findByStatusAndExpiresAtLessThanEqual(eq(SpaceEnquiryStatus.PENDING), any()))
                .thenReturn(List.of(pending));
        when(enquiryRepository.expirePendingDue(any())).thenReturn(1);

        assertThat(service.expireDue()).isEqualTo(1);
        verify(emailService, never()).send(any());
        ArgumentCaptor<PublishNotificationCommand> captor = ArgumentCaptor.forClass(PublishNotificationCommand.class);
        verify(notificationService).publish(captor.capture());
        assertThat(captor.getValue().getNotificationType()).isEqualTo(NotificationType.CONTACT_ENQUIRY_EXPIRED);
        assertThat(captor.getValue().getMessage()).doesNotContain("9991110001");
    }

    @Test
    void adminCanExpirePendingEnquiryWithoutEmail() {
        SpaceEnquiryEntity pending = pendingEnquiry();
        stubAdminDetail(pending);

        AdminSpaceEnquiryDetailResponse expired = service.expire(pending.getId(), adminId);

        assertThat(expired.getStatus()).isEqualTo(SpaceEnquiryStatus.EXPIRED);
        assertThat(pending.getExpiresAt()).isEqualTo(LocalDateTime.now(clock));
        assertThat(pending.getReviewedAt()).isEqualTo(LocalDateTime.now(clock));
        verify(emailService, never()).send(any());
        ArgumentCaptor<PublishNotificationCommand> captor = ArgumentCaptor.forClass(PublishNotificationCommand.class);
        verify(notificationService).publish(captor.capture());
        assertThat(captor.getValue().getNotificationType()).isEqualTo(NotificationType.CONTACT_ENQUIRY_EXPIRED);
        assertThat(captor.getValue().getMessage())
                .contains("Orchid Stay PG")
                .doesNotContain("9991110001");
    }

    @Test
    void adminCanExpireSharedEnquiryWithoutEmail() {
        SpaceEnquiryEntity shared = pendingEnquiry();
        shared.setStatus(SpaceEnquiryStatus.SHARED);
        shared.setSharedAt(LocalDateTime.now(clock));
        shared.setSharedByAdminId(adminId);
        stubAdminDetail(shared);

        AdminSpaceEnquiryDetailResponse expired = service.expire(shared.getId(), adminId);

        assertThat(expired.getStatus()).isEqualTo(SpaceEnquiryStatus.EXPIRED);
        verify(emailService, never()).send(any());
        ArgumentCaptor<PublishNotificationCommand> captor = ArgumentCaptor.forClass(PublishNotificationCommand.class);
        verify(notificationService).publish(captor.capture());
        assertThat(captor.getValue().getNotificationType()).isEqualTo(NotificationType.CONTACT_ENQUIRY_EXPIRED);
        assertThat(captor.getValue().getMessage()).doesNotContain("9991110001");
    }

    @Test
    void expireAlreadyExpiredEnquiryIsIdempotent() {
        SpaceEnquiryEntity expired = pendingEnquiry();
        expired.setStatus(SpaceEnquiryStatus.EXPIRED);
        stubAdminDetail(expired);

        AdminSpaceEnquiryDetailResponse detail = service.expire(expired.getId(), adminId);

        assertThat(detail.getStatus()).isEqualTo(SpaceEnquiryStatus.EXPIRED);
        verify(emailService, never()).send(any());
        verify(notificationService, never()).publish(any());
        verify(enquiryRepository, never()).save(any());
    }

    @Test
    void expireRejectedEnquiryConflicts() {
        SpaceEnquiryEntity rejected = pendingEnquiry();
        rejected.setStatus(SpaceEnquiryStatus.REJECTED);
        when(enquiryRepository.findById(rejected.getId())).thenReturn(Optional.of(rejected));

        assertThatThrownBy(() -> service.expire(rejected.getId(), adminId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo("ENQUIRY_NOT_ACTIVE");
        verify(emailService, never()).send(any());
        verify(notificationService, never()).publish(any());
    }

    @Test
    void rejectPendingEnquiry() {
        SpaceEnquiryEntity pending = pendingEnquiry();
        when(enquiryRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
        when(spaceRepository.findById(spaceBId)).thenReturn(Optional.of(spaceB));
        when(ownerContactResolver.resolveOrEmpty(spaceB)).thenReturn(shareableContact());

        AdminSpaceEnquiryDetailResponse rejected = service.reject(pending.getId(), adminId, "Not a fit");

        assertThat(rejected.getStatus()).isEqualTo(SpaceEnquiryStatus.REJECTED);
        assertThat(rejected.getRejectionReason()).isEqualTo("Not a fit");
        ArgumentCaptor<SendEmailCommand> mailCaptor = ArgumentCaptor.forClass(SendEmailCommand.class);
        verify(emailService, times(1)).send(mailCaptor.capture());
        SendEmailCommand rejectedMail = mailCaptor.getValue();
        assertThat(rejectedMail.getEventType()).isEqualTo(EmailEventType.ENQUIRY_REJECTED);
        assertThat(rejectedMail.getRecipientEmail()).isEqualTo("ketan@example.com");
        assertThat(rejectedMail.getIdempotencyKey()).isEqualTo(EmailIdempotencyKeys.enquiryRejected(pending.getId()));
        assertThat(rejectedMail.getPlainBody()).contains("Orchid Stay PG");
        assertThat(rejectedMail.getPlainBody()).doesNotContain("Not a fit");
        assertThat(rejectedMail.getPlainBody()).doesNotContain("9991110001");
        assertThat(rejectedMail.getPlainBody()).doesNotContain("Owner contact");
        ArgumentCaptor<PublishNotificationCommand> captor = ArgumentCaptor.forClass(PublishNotificationCommand.class);
        verify(notificationService).publish(captor.capture());
        assertThat(captor.getValue().getNotificationType()).isEqualTo(NotificationType.CONTACT_ENQUIRY_REJECTED);
        assertThat(captor.getValue().getMessage())
                .contains("Orchid Stay PG")
                .doesNotContain("Not a fit")
                .doesNotContain("9991110001");
    }

    @Test
    void shareConflictWhenOwnerContactMissing() {
        SpaceEnquiryEntity pending = pendingEnquiry();
        OwnerContactResponse empty = OwnerContactResponse.builder().available(false).build();
        when(enquiryRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
        when(userRepository.findByIdAndIsActiveTrue(memberId)).thenReturn(Optional.of(member));
        when(spaceRepository.findByIdAndIsActiveTrue(spaceBId)).thenReturn(Optional.of(spaceB));
        when(ownerContactResolver.resolve(spaceB)).thenReturn(empty);
        when(ownerContactResolver.hasShareableContact(empty)).thenReturn(false);

        assertThatThrownBy(() -> service.share(pending.getId(), adminId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo("OWNER_CONTACT_UNAVAILABLE");
        verify(emailService, never()).send(any());
    }

    private void stubCreateSuccess(UserEntity requester, SpaceEntity space, boolean registeredOwner) {
        when(userRepository.findByIdAndIsActiveTrue(requester.getId())).thenReturn(Optional.of(requester));
        when(spaceRepository.findByIdAndIsActiveTrueAndDiscoverableTrue(space.getId())).thenReturn(Optional.of(space));
        when(spaceRepository.existsByIdAndOwnerIdAndIsActiveTrue(space.getId(), requester.getId())).thenReturn(false);
        when(enquiryRepository.findBySpaceIdAndRequesterUserIdAndStatus(
                        eq(space.getId()), eq(requester.getId()), eq(SpaceEnquiryStatus.PENDING)))
                .thenReturn(Optional.empty());
        when(enquiryRepository.findBySpaceIdAndRequesterUserIdAndStatus(
                        eq(space.getId()), eq(requester.getId()), eq(SpaceEnquiryStatus.SHARED)))
                .thenReturn(Optional.empty());
        when(spaceRepository.existsByOwnerIdAndIsActiveTrue(requester.getId())).thenReturn(registeredOwner);
        when(enquiryAutoSharePolicy.evaluate(any(), any()))
                .thenReturn(EnquiryAutoShareDecision.deny(EnquiryAutoShareReason.OWNER_NOT_LINKED));
    }

    private static SendEmailCommand mailOf(
            ArgumentCaptor<SendEmailCommand> captor, EmailEventType eventType) {
        return captor.getAllValues().stream()
                .filter(command -> command.getEventType() == eventType)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing email event " + eventType));
    }

    private SpaceEnquiryEntity pendingEnquiry() {
        SpaceEnquiryEntity entity = SpaceEnquiryEntity.builder()
                .spaceId(spaceBId)
                .spaceNameSnapshot("Orchid Stay PG")
                .requesterUserId(memberId)
                .requesterNameSnapshot("Ketan")
                .requesterEmail("ketan@example.com")
                .requesterType(EnquiryRequesterType.MEMBER)
                .clientChannel(InquiryClientChannel.WEB)
                .status(SpaceEnquiryStatus.PENDING)
                .requestedAt(LocalDateTime.now(clock))
                .expiresAt(LocalDateTime.now(clock).plusDays(30))
                .build();
        entity.setId(UUID.randomUUID());
        return entity;
    }

    private void stubAdminDetail(SpaceEnquiryEntity enquiry) {
        when(enquiryRepository.findById(enquiry.getId())).thenReturn(Optional.of(enquiry));
        when(spaceRepository.findById(spaceBId)).thenReturn(Optional.of(spaceB));
        when(ownerContactResolver.resolveOrEmpty(spaceB)).thenReturn(shareableContact());
    }

    private static OwnerContactResponse shareableContact() {
        return OwnerContactResponse.builder()
                .ownerName("Owner A")
                .mobileNumber("9991110001")
                .available(true)
                .build();
    }

    private static UserEntity user(String name, String mobile, String email, UUID id, SystemRole role) {
        UserEntity entity = UserEntity.builder()
                .fullName(name)
                .mobileNumber(mobile)
                .email(email)
                .systemRole(role)
                .isActive(true)
                .build();
        entity.setId(id);
        return entity;
    }

    private static SpaceEntity space(String name, UUID id, UserEntity owner) {
        SpaceEntity entity = SpaceEntity.builder()
                .name(name)
                .type(SpaceType.PG)
                .address("Wakad, Pune")
                .isActive(true)
                .discoverable(true)
                .owner(owner)
                .build();
        entity.setId(id);
        return entity;
    }
}
