package com.acomi.acomi_backend.enquiry.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.enquiry.api.dto.request.CreateSpaceEnquiryRequest;
import com.acomi.acomi_backend.enquiry.api.dto.response.SpaceEnquiryResponse;
import com.acomi.acomi_backend.enquiry.domain.model.EnquiryRequesterType;
import com.acomi.acomi_backend.enquiry.domain.model.SpaceEnquiryStatus;
import com.acomi.acomi_backend.enquiry.domain.policy.EnquiryAutoSharePolicy;
import com.acomi.acomi_backend.enquiry.infrastructure.persistence.entity.SpaceEnquiryEntity;
import com.acomi.acomi_backend.enquiry.infrastructure.persistence.repository.SpaceEnquiryDeliveryRepository;
import com.acomi.acomi_backend.enquiry.infrastructure.persistence.repository.SpaceEnquiryRepository;
import com.acomi.acomi_backend.mail.application.dto.SendEmailCommand;
import com.acomi.acomi_backend.mail.application.service.EmailService;
import com.acomi.acomi_backend.mail.config.MailProperties;
import com.acomi.acomi_backend.mail.domain.model.EmailEventType;
import com.acomi.acomi_backend.mess.infrastructure.persistence.entity.MessRegistrationEntity;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.notification.application.port.in.PublishNotificationCommand;
import com.acomi.acomi_backend.notification.application.service.NotificationService;
import com.acomi.acomi_backend.notification.domain.model.NotificationType;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.acomi.acomi_backend.registration.application.AdminLeadDefaults;
import com.acomi.acomi_backend.space.application.service.SpaceAmenityService;
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

/**
 * Phase 6: converted listing → owner link eligibility → enquiry auto-share, using the real
 * {@link EnquiryAutoSharePolicy} and {@link OwnerContactResolver}.
 */
@ExtendWith(MockitoExtension.class)
class ConvertedListingEnquiryFlowTest {

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
    private EmailService emailService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private SpaceAmenityService spaceAmenityService;

    @Mock
    private com.acomi.acomi_backend.inquirycredit.application.service.InquiryAccessService inquiryAccessService;

    private SpaceEnquiryService service;
    private EnquiryAutoSharePolicy policy;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-08T06:00:00Z"), ZoneId.of("Asia/Kolkata"));

    private UUID requesterId;
    private UUID ownerId;
    private UUID adminId;
    private UUID spaceId;
    private UserEntity requester;
    private UserEntity owner;
    private UserEntity admin;
    private SpaceEntity space;

    @BeforeEach
    void setUp() {
        OwnerContactResolver resolver =
                new OwnerContactResolver(propertyRegistrationRepository, messRegistrationRepository);
        EnquiryListingDetailsResolver listingDetailsResolver = new EnquiryListingDetailsResolver(
                propertyRegistrationRepository, messRegistrationRepository, spaceAmenityService);
        policy = new EnquiryAutoSharePolicy(
                resolver, propertyRegistrationRepository, messRegistrationRepository, true);
        MailProperties mailProperties = new MailProperties();
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
                resolver,
                listingDetailsResolver,
                policy,
                emailService,
                notificationService,
                mailProperties,
                clock,
                30,
                inquiryAccessService);
        lenient().when(inquiryAccessService.authorizeNewEnquiry(any(), any()))
                .thenReturn(com.acomi.acomi_backend.inquirycredit.domain.model.InquiryAccessGrant.FREE_WEB);
        lenient().when(deliveryRepository.findActiveAppDelivery(any(), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(deliveryRepository.findActiveEmailDelivery(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(deliveryRepository.findAppDeliveryForUpdate(any(), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(deliveryRepository.findEmailDeliveryForUpdate(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(deliveryRepository.existsBySpaceIdAndRequesterUserIdAndDeliveryChannel(any(), any(), any()))
                .thenReturn(false);
        lenient().when(deliveryRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            var entity = invocation.getArgument(0);
            return entity;
        });
        lenient().when(deliveryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        requesterId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        spaceId = UUID.randomUUID();
        requester = user("Ketan", "9876500001", "ketan@example.com", requesterId, SystemRole.USER);
        owner = user("Rahul", "9000000008", "rahul@example.com", ownerId, SystemRole.USER);
        admin = user("Admin", "9000000001", "admin@acomi.in", adminId, SystemRole.ADMIN);
        space = SpaceEntity.builder()
                .name("Sunrise PG")
                .type(SpaceType.PG)
                .address("Wakad, Pune")
                .isActive(true)
                .discoverable(true)
                .owner(admin)
                .build();
        space.setId(spaceId);
        lenient().when(spaceAmenityService.getForSpace(any())).thenReturn(List.of());
    }

    @Test
    void unlinkedAdminHeldListingAutoSharesListingMobileNotAdmin() {
        PropertyRegistrationEntity registration = unlinkedRegistration("9991110001");
        stubCreateLookup(space);
        stubRegistration(registration, null);
        stubPersist();
        when(enquiryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SpaceEnquiryResponse response = service.create(requesterId, spaceId, new CreateSpaceEnquiryRequest());

        assertThat(response.getStatus()).isEqualTo(SpaceEnquiryStatus.SHARED);
        assertThat(response.isDetailsShared()).isTrue();
        assertThat(policy.evaluate(space, "ketan@example.com").isAllowed()).isTrue();

        ArgumentCaptor<SendEmailCommand> mailCaptor = ArgumentCaptor.forClass(SendEmailCommand.class);
        // WEB auto-share defers owner-contact ENQUIRY_SHARED until explicit deliverContactByEmail.
        verify(emailService, times(1)).send(mailCaptor.capture());
        assertThat(mailCaptor.getValue().getEventType()).isEqualTo(EmailEventType.ENQUIRY_SUBMITTED_SUPPORT);
        assertThat(mailCaptor.getValue().getPlainBody()).contains("shared automatically");
        verify(userRepository, never()).findBySystemRoleAndIsActiveTrue(SystemRole.ADMIN);
    }

    @Test
    void linkedEligibleConvertedListingAutoSharesCorrectOwnerMobile() {
        space.setOwner(owner);
        PropertyRegistrationEntity registration = linkedRegistration("9991110001");
        stubCreateLookup(space);
        stubRegistration(registration, null);
        stubPersist();
        when(enquiryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SpaceEnquiryResponse response = service.create(requesterId, spaceId, new CreateSpaceEnquiryRequest());

        assertThat(response.getStatus()).isEqualTo(SpaceEnquiryStatus.SHARED);
        assertThat(response.isDetailsShared()).isTrue();
        assertThat(response.getSharedAt()).isNotNull();

        ArgumentCaptor<SpaceEnquiryEntity> saved = ArgumentCaptor.forClass(SpaceEnquiryEntity.class);
        verify(enquiryRepository).save(saved.capture());
        assertThat(saved.getValue().getSharedByAdminId()).isNull();
        assertThat(saved.getValue().getStatus()).isEqualTo(SpaceEnquiryStatus.SHARED);

        ArgumentCaptor<SendEmailCommand> mailCaptor = ArgumentCaptor.forClass(SendEmailCommand.class);
        // WEB: owner-contact email deferred; only support submission mail on create/auto-share.
        verify(emailService, times(1)).send(mailCaptor.capture());
        SendEmailCommand support = mailCaptor.getValue();
        assertThat(support.getEventType()).isEqualTo(EmailEventType.ENQUIRY_SUBMITTED_SUPPORT);
        assertThat(support.getRecipientEmail()).isEqualTo("support@acomi.in");
        assertThat(support.getPlainBody()).contains("shared automatically");
        assertThat(support.getPlainBody()).doesNotContain("9000000008");
        assertThat(support.getPlainBody()).doesNotContain("Owner contact");

        ArgumentCaptor<PublishNotificationCommand> notes =
                ArgumentCaptor.forClass(PublishNotificationCommand.class);
        verify(notificationService, times(2)).publish(notes.capture());
        assertThat(notes.getAllValues())
                .extracting(PublishNotificationCommand::getNotificationType)
                .containsExactly(
                        NotificationType.CONTACT_ENQUIRY_SUBMITTED, NotificationType.CONTACT_ENQUIRY_SHARED);
        notes.getAllValues().forEach(note -> {
            assertThat(note.getMessage()).doesNotContain("9000000008");
            assertThat(note.getMessage()).doesNotContain("rahul@example.com");
            assertThat(note.getMessage()).doesNotContain("9991110001");
        });
        verify(userRepository, never()).findBySystemRoleAndIsActiveTrue(SystemRole.ADMIN);
    }

    @Test
    void secondCreateAfterAutoShareReusesSharedEnquiryWithoutNewEmails() {
        space.setOwner(owner);
        PropertyRegistrationEntity registration = linkedRegistration("9991110001");
        SpaceEnquiryEntity shared = pendingEnquiry();
        shared.setStatus(SpaceEnquiryStatus.SHARED);
        shared.setSharedAt(LocalDateTime.now(clock));
        when(userRepository.findByIdAndIsActiveTrue(requesterId)).thenReturn(Optional.of(requester));
        when(spaceRepository.findByIdAndIsActiveTrueAndDiscoverableTrue(spaceId)).thenReturn(Optional.of(space));
        when(spaceRepository.existsByIdAndOwnerIdAndIsActiveTrue(spaceId, requesterId)).thenReturn(false);
        when(enquiryRepository.findBySpaceIdAndRequesterUserIdAndStatus(
                        spaceId, requesterId, SpaceEnquiryStatus.PENDING))
                .thenReturn(Optional.empty());
        when(enquiryRepository.findBySpaceIdAndRequesterUserIdAndStatus(
                        spaceId, requesterId, SpaceEnquiryStatus.SHARED))
                .thenReturn(Optional.of(shared));
        stubRegistration(registration, null);

        SpaceEnquiryResponse response = service.create(requesterId, spaceId, new CreateSpaceEnquiryRequest());

        assertThat(policy.evaluate(space, "ketan@example.com").isAllowed()).isTrue();
        assertThat(response.getEnquiryId()).isEqualTo(shared.getId());
        assertThat(response.getStatus()).isEqualTo(SpaceEnquiryStatus.SHARED);
        verify(enquiryRepository, never()).saveAndFlush(any());
        verify(emailService, never()).send(any());
        verify(notificationService, never()).publish(any());
    }

    @Test
    void existingPendingEnquiryIsAutoSharedWhenListingIsEligible() {
        space.setOwner(owner);
        PropertyRegistrationEntity registration = linkedRegistration("9991110001");
        SpaceEnquiryEntity pending = pendingEnquiry();
        when(userRepository.findByIdAndIsActiveTrue(requesterId)).thenReturn(Optional.of(requester));
        when(spaceRepository.findByIdAndIsActiveTrueAndDiscoverableTrue(spaceId)).thenReturn(Optional.of(space));
        when(spaceRepository.existsByIdAndOwnerIdAndIsActiveTrue(spaceId, requesterId)).thenReturn(false);
        when(enquiryRepository.findBySpaceIdAndRequesterUserIdAndStatus(
                        spaceId, requesterId, SpaceEnquiryStatus.PENDING))
                .thenReturn(Optional.of(pending));
        when(enquiryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubRegistration(registration, null);

        SpaceEnquiryResponse response = service.create(requesterId, spaceId, new CreateSpaceEnquiryRequest());

        assertThat(policy.evaluate(space, "ketan@example.com").isAllowed()).isTrue();
        assertThat(response.getEnquiryId()).isEqualTo(pending.getId());
        assertThat(response.getStatus()).isEqualTo(SpaceEnquiryStatus.SHARED);
        // WEB auto-share of an existing PENDING enquiry does not send owner-contact email yet.
        verify(emailService, never()).send(any());
    }

    @Test
    void linkedInactiveOwnerEnquiryStaysPending() {
        owner.setActive(false);
        space.setOwner(owner);
        stubCreateLookup(space);
        stubRegistration(linkedRegistration("9991110001"), null);
        when(userRepository.findBySystemRoleAndIsActiveTrue(SystemRole.ADMIN)).thenReturn(List.of(admin));
        stubPersist();

        SpaceEnquiryResponse response = service.create(requesterId, spaceId, new CreateSpaceEnquiryRequest());

        assertThat(response.getStatus()).isEqualTo(SpaceEnquiryStatus.PENDING);
        assertThat(policy.evaluate(space, "ketan@example.com").reason().name()).isEqualTo("OWNER_INACTIVE");
        verify(emailService, times(1)).send(any());
        verifyNoSharedEmail();
    }

    @Test
    void linkedOwnerWithoutShareableMobileEnquiryStaysPending() {
        owner.setMobileNumber(null);
        owner.setEmail("rahul@example.com");
        space.setOwner(owner);
        space.setContactNumber(null);
        PropertyRegistrationEntity registration = linkedRegistration(null);
        stubCreateLookup(space);
        stubRegistration(registration, null);
        when(userRepository.findBySystemRoleAndIsActiveTrue(SystemRole.ADMIN)).thenReturn(List.of(admin));
        stubPersist();

        SpaceEnquiryResponse response = service.create(requesterId, spaceId, new CreateSpaceEnquiryRequest());

        assertThat(response.getStatus()).isEqualTo(SpaceEnquiryStatus.PENDING);
        assertThat(policy.evaluate(space, "ketan@example.com").reason().name())
                .isEqualTo("OWNER_CONTACT_UNAVAILABLE");
        verifyNoSharedEmail();
    }

    @Test
    void discoverableTestLeadCannotAutoShare() {
        space.setOwner(owner);
        PropertyRegistrationEntity registration = linkedRegistration("9000000008");
        registration.setTestLead(true);
        stubCreateLookup(space);
        stubRegistration(registration, null);
        when(userRepository.findBySystemRoleAndIsActiveTrue(SystemRole.ADMIN)).thenReturn(List.of(admin));
        stubPersist();

        SpaceEnquiryResponse response = service.create(requesterId, spaceId, new CreateSpaceEnquiryRequest());

        assertThat(response.getStatus()).isEqualTo(SpaceEnquiryStatus.PENDING);
        assertThat(policy.evaluate(space, "ketan@example.com").reason().name()).isEqualTo("TEST_LISTING");
        verifyNoSharedEmail();
    }

    @Test
    void nonDiscoverableConvertedListingCannotBeEnquired() {
        space.setDiscoverable(false);
        when(userRepository.findByIdAndIsActiveTrue(requesterId)).thenReturn(Optional.of(requester));
        when(spaceRepository.findByIdAndIsActiveTrueAndDiscoverableTrue(spaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(requesterId, spaceId, new CreateSpaceEnquiryRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
        space.setOwner(owner);
        assertThat(policy.evaluate(space, "ketan@example.com").reason().name())
                .isEqualTo("SPACE_NOT_DISCOVERABLE");
        verify(enquiryRepository, never()).saveAndFlush(any());
        verify(emailService, never()).send(any());
    }

    @Test
    void requesterEmailMissingIsAllowedAndPersists() {
        requester.setEmail(null);
        stubCreateLookup(space);
        stubRegistration(null, null);
        when(userRepository.findBySystemRoleAndIsActiveTrue(SystemRole.ADMIN)).thenReturn(List.of(admin));
        stubPersist();

        SpaceEnquiryResponse response = service.create(requesterId, spaceId, new CreateSpaceEnquiryRequest());

        assertThat(response.getRequesterEmail()).isNull();
        ArgumentCaptor<SpaceEnquiryEntity> saved = ArgumentCaptor.forClass(SpaceEnquiryEntity.class);
        verify(enquiryRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getRequesterEmail()).isNull();
    }

    @Test
    void linkedEligibleMessListingAutoShares() {
        space.setType(SpaceType.MESS);
        space.setName("Sunrise Mess");
        space.setOwner(owner);
        MessRegistrationEntity mess = MessRegistrationEntity.builder()
                .messName("Sunrise Mess")
                .ownerName("Imported Mess")
                .mobileNumber("8881110001")
                .linkedOwnerUserId(ownerId)
                .testLead(false)
                .convertedSpaceId(spaceId)
                .build();
        stubCreateLookup(space);
        stubRegistration(null, mess);
        stubPersist();
        when(enquiryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SpaceEnquiryResponse response = service.create(requesterId, spaceId, new CreateSpaceEnquiryRequest());

        assertThat(response.getStatus()).isEqualTo(SpaceEnquiryStatus.SHARED);
        ArgumentCaptor<SendEmailCommand> mailCaptor = ArgumentCaptor.forClass(SendEmailCommand.class);
        verify(emailService, times(1)).send(mailCaptor.capture());
        assertThat(mailCaptor.getValue().getEventType()).isEqualTo(EmailEventType.ENQUIRY_SUBMITTED_SUPPORT);
        verifyNoSharedEmail();
    }

    @Test
    void memberEnquiryResponseOmitsOwnerContactAfterAutoShare() {
        space.setOwner(owner);
        stubCreateLookup(space);
        stubRegistration(linkedRegistration("9991110001"), null);
        stubPersist();
        when(enquiryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SpaceEnquiryResponse response = service.create(requesterId, spaceId, new CreateSpaceEnquiryRequest());

        assertThat(response.getStatus()).isEqualTo(SpaceEnquiryStatus.SHARED);
        assertThat(response.isDetailsShared()).isTrue();
        assertThat(response.getRequesterEmail()).isEqualTo("ketan@example.com");
    }

    @Test
    void adminHeldConvertedListingAutoSharesLeadAlternateNotAdminMobile() {
        space.setName("Lovely Home's PG 3");
        space.setAddress(AdminLeadDefaults.PLACEHOLDER_PINCODE);
        PropertyRegistrationEntity registration = PropertyRegistrationEntity.builder()
                .propertyName("Lovely Home's PG 3")
                .ownerName("Unknown")
                .mobileNumber("6000000000")
                .alternateMobileNumber("7722085599")
                .addressLine(AdminLeadDefaults.PLACEHOLDER_ADDRESS)
                .city(AdminLeadDefaults.PLACEHOLDER_ADDRESS)
                .state(AdminLeadDefaults.PLACEHOLDER_ADDRESS)
                .pincode(AdminLeadDefaults.PLACEHOLDER_PINCODE)
                .linkedOwnerUserId(null)
                .convertedSpaceId(spaceId)
                .testLead(false)
                .build();
        stubCreateLookup(space);
        stubRegistration(registration, null);
        stubPersist();
        when(enquiryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SpaceEnquiryResponse response = service.create(requesterId, spaceId, new CreateSpaceEnquiryRequest());

        assertThat(response.getStatus()).isEqualTo(SpaceEnquiryStatus.SHARED);
        ArgumentCaptor<SendEmailCommand> mailCaptor = ArgumentCaptor.forClass(SendEmailCommand.class);
        verify(emailService, times(1)).send(mailCaptor.capture());
        assertThat(mailCaptor.getValue().getEventType()).isEqualTo(EmailEventType.ENQUIRY_SUBMITTED_SUPPORT);
        assertThat(mailCaptor.getValue().getPlainBody()).contains("shared automatically");
        verifyNoSharedEmail();
    }

    @Test
    void adminOwnerWithoutConvertedRegistrationDoesNotAutoShare() {
        stubCreateLookup(space);
        stubRegistration(null, null);
        when(userRepository.findBySystemRoleAndIsActiveTrue(SystemRole.ADMIN)).thenReturn(List.of(admin));
        stubPersist();

        SpaceEnquiryResponse response = service.create(requesterId, spaceId, new CreateSpaceEnquiryRequest());

        assertThat(response.getStatus()).isEqualTo(SpaceEnquiryStatus.PENDING);
        assertThat(policy.evaluate(space, "ketan@example.com").reason().name()).isEqualTo("OWNER_NOT_LINKED");
        verifyNoSharedEmail();
    }

    private void stubCreateLookup(SpaceEntity listing) {
        when(userRepository.findByIdAndIsActiveTrue(requesterId)).thenReturn(Optional.of(requester));
        when(spaceRepository.findByIdAndIsActiveTrueAndDiscoverableTrue(spaceId)).thenReturn(Optional.of(listing));
        when(spaceRepository.existsByIdAndOwnerIdAndIsActiveTrue(spaceId, requesterId)).thenReturn(false);
        when(enquiryRepository.findBySpaceIdAndRequesterUserIdAndStatus(
                        eq(spaceId), eq(requesterId), eq(SpaceEnquiryStatus.PENDING)))
                .thenReturn(Optional.empty());
        when(enquiryRepository.findBySpaceIdAndRequesterUserIdAndStatus(
                        eq(spaceId), eq(requesterId), eq(SpaceEnquiryStatus.SHARED)))
                .thenReturn(Optional.empty());
        when(spaceRepository.existsByOwnerIdAndIsActiveTrue(requesterId)).thenReturn(false);
    }

    private void stubRegistration(PropertyRegistrationEntity property, MessRegistrationEntity mess) {
        when(propertyRegistrationRepository.findByConvertedSpaceId(spaceId))
                .thenReturn(Optional.ofNullable(property));
        when(messRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.ofNullable(mess));
    }

    private void stubPersist() {
        when(enquiryRepository.saveAndFlush(any(SpaceEnquiryEntity.class))).thenAnswer(invocation -> {
            SpaceEnquiryEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            return entity;
        });
    }

    private void verifyNoSharedEmail() {
        ArgumentCaptor<SendEmailCommand> mailCaptor = ArgumentCaptor.forClass(SendEmailCommand.class);
        verify(emailService, times(1)).send(mailCaptor.capture());
        assertThat(mailCaptor.getAllValues())
                .extracting(SendEmailCommand::getEventType)
                .doesNotContain(EmailEventType.ENQUIRY_SHARED);
    }

    private PropertyRegistrationEntity unlinkedRegistration(String contactOne) {
        return PropertyRegistrationEntity.builder()
                .propertyName("Sunrise PG")
                .ownerName("Imported Owner")
                .mobileNumber(contactOne)
                .linkedOwnerUserId(null)
                .convertedSpaceId(spaceId)
                .testLead(false)
                .build();
    }

    private PropertyRegistrationEntity linkedRegistration(String contactOne) {
        return PropertyRegistrationEntity.builder()
                .propertyName("Sunrise PG")
                .ownerName("Imported Owner")
                .mobileNumber(contactOne)
                .linkedOwnerUserId(ownerId)
                .convertedSpaceId(spaceId)
                .testLead(false)
                .build();
    }

    private SpaceEnquiryEntity pendingEnquiry() {
        SpaceEnquiryEntity entity = SpaceEnquiryEntity.builder()
                .spaceId(spaceId)
                .spaceNameSnapshot("Sunrise PG")
                .requesterUserId(requesterId)
                .requesterNameSnapshot("Ketan")
                .requesterEmail("ketan@example.com")
                .requesterType(EnquiryRequesterType.MEMBER)
                .status(SpaceEnquiryStatus.PENDING)
                .requestedAt(LocalDateTime.now(clock))
                .expiresAt(LocalDateTime.now(clock).plusDays(30))
                .build();
        entity.setId(UUID.randomUUID());
        return entity;
    }

    private static SendEmailCommand mailOf(ArgumentCaptor<SendEmailCommand> captor, EmailEventType eventType) {
        return captor.getAllValues().stream()
                .filter(command -> command.getEventType() == eventType)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing email event " + eventType));
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
}
