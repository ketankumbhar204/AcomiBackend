package com.acomi.acomi_backend.enquiry.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.common.exception.AlreadyDeliveredException;
import com.acomi.acomi_backend.enquiry.api.dto.request.CreateSpaceEnquiryRequest;
import com.acomi.acomi_backend.enquiry.api.dto.response.OwnerContactResponse;
import com.acomi.acomi_backend.enquiry.api.dto.response.SpaceEnquiryResponse;
import com.acomi.acomi_backend.enquiry.domain.model.EnquiryDeliveryChannel;
import com.acomi.acomi_backend.enquiry.domain.model.EnquiryRequesterType;
import com.acomi.acomi_backend.enquiry.domain.model.SpaceEnquiryStatus;
import com.acomi.acomi_backend.enquiry.domain.policy.EnquiryAutoShareDecision;
import com.acomi.acomi_backend.enquiry.domain.policy.EnquiryAutoSharePolicy;
import com.acomi.acomi_backend.enquiry.domain.policy.EnquiryAutoShareReason;
import com.acomi.acomi_backend.enquiry.infrastructure.persistence.entity.SpaceEnquiryDeliveryEntity;
import com.acomi.acomi_backend.enquiry.infrastructure.persistence.entity.SpaceEnquiryEntity;
import com.acomi.acomi_backend.enquiry.infrastructure.persistence.repository.SpaceEnquiryDeliveryRepository;
import com.acomi.acomi_backend.enquiry.infrastructure.persistence.repository.SpaceEnquiryRepository;
import com.acomi.acomi_backend.inquirycredit.application.service.InquiryAccessService;
import com.acomi.acomi_backend.inquirycredit.domain.model.InquiryAccessGrant;
import com.acomi.acomi_backend.inquirycredit.domain.model.InquiryClientChannel;
import com.acomi.acomi_backend.mail.application.service.EmailService;
import com.acomi.acomi_backend.mail.config.MailProperties;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.notification.application.service.NotificationService;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpaceEnquiryChannelDeliveryTest {

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
    private InquiryAccessService inquiryAccessService;

    private SpaceEnquiryService service;
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-17T09:05:00Z"), ZoneId.of("Asia/Kolkata"));

    private UUID memberId;
    private UUID spaceId;
    private UserEntity member;
    private SpaceEntity space;

    @BeforeEach
    void setUp() {
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
                ownerContactResolver,
                listingDetailsResolver,
                enquiryAutoSharePolicy,
                emailService,
                notificationService,
                mailProperties,
                clock,
                30,
                inquiryAccessService);

        memberId = UUID.randomUUID();
        spaceId = UUID.randomUUID();
        member = UserEntity.builder()
                .fullName("Ketan")
                .mobileNumber("9876500001")
                .email("ketan@example.com")
                .systemRole(SystemRole.USER)
                .isActive(true)
                .build();
        member.setId(memberId);
        space = SpaceEntity.builder()
                .name("Sunrise PG")
                .type(SpaceType.PG)
                .address("Pune")
                .isActive(true)
                .discoverable(true)
                .owner(UserEntity.builder()
                        .fullName("Owner")
                        .mobileNumber("9000000002")
                        .systemRole(SystemRole.USER)
                        .isActive(true)
                        .build())
                .build();
        space.setId(spaceId);
        space.getOwner().setId(UUID.randomUUID());

        Mockito.lenient().when(inquiryAccessService.authorizeNewEnquiry(any(), any()))
                .thenReturn(InquiryAccessGrant.FREE_WEB);
        Mockito.lenient().when(listingDetailsResolver.resolve(any()))
                .thenReturn(EnquiryListingDetails.empty());
        Mockito.lenient().when(deliveryRepository.findActiveAppDelivery(any(), any(), any()))
                .thenReturn(Optional.empty());
        Mockito.lenient().when(deliveryRepository.findActiveEmailDelivery(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        Mockito.lenient().when(deliveryRepository.findAppDeliveryForUpdate(any(), any(), any()))
                .thenReturn(Optional.empty());
        Mockito.lenient().when(deliveryRepository.findEmailDeliveryForUpdate(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        Mockito.lenient()
                .when(deliveryRepository.existsBySpaceIdAndRequesterUserIdAndDeliveryChannel(any(), any(), any()))
                .thenReturn(false);
        Mockito.lenient().when(deliveryRepository.saveAndFlush(any())).thenAnswer(inv -> {
            SpaceEnquiryDeliveryEntity d = inv.getArgument(0);
            if (d.getId() == null) {
                d.setId(UUID.randomUUID());
            }
            return d;
        });
        Mockito.lenient().when(deliveryRepository.save(any())).thenAnswer(inv -> {
            SpaceEnquiryDeliveryEntity d = inv.getArgument(0);
            if (d.getId() == null) {
                d.setId(UUID.randomUUID());
            }
            return d;
        });
    }

    @Test
    void appDeliveryAllowedWhenNoneExists() {
        stubDiscoverableMember();
        SpaceEnquiryEntity created = stubNewSharedEnquiry();
        CreateSpaceEnquiryRequest request = new CreateSpaceEnquiryRequest();
        request.setDeliveryChannel(EnquiryDeliveryChannel.APP);

        SpaceEnquiryResponse response =
                service.create(memberId, spaceId, request, InquiryClientChannel.WEB);

        assertThat(created).isNotNull();
        assertThat(response.getDeliveryChannel()).isEqualTo(EnquiryDeliveryChannel.APP);
        assertThat(response.getDeliveredAt()).isNotNull();
        assertThat(response.isAlreadyDelivered()).isFalse();
        verify(inquiryAccessService)
                .consumeAfterSuccessfulCreate(
                        eq(memberId), eq(InquiryClientChannel.WEB), any(), eq(response.getEnquiryId()));
        ArgumentCaptor<SpaceEnquiryDeliveryEntity> captor =
                ArgumentCaptor.forClass(SpaceEnquiryDeliveryEntity.class);
        verify(deliveryRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getDeliveryChannel()).isEqualTo(EnquiryDeliveryChannel.APP);
        assertThat(captor.getValue().getRecipientEmail()).isNull();
    }

    @Test
    void activeAppDeliveryBlocksAnotherAppDeliveryWithoutConsume() {
        stubDiscoverableMember();
        LocalDateTime deliveredAt = LocalDateTime.now(clock).minusDays(1);
        SpaceEnquiryDeliveryEntity active = SpaceEnquiryDeliveryEntity.builder()
                .enquiryId(UUID.randomUUID())
                .spaceId(spaceId)
                .requesterUserId(memberId)
                .deliveryChannel(EnquiryDeliveryChannel.APP)
                .deliveredAt(deliveredAt)
                .expiresAt(LocalDateTime.now(clock).plusDays(20))
                .build();
        active.setId(UUID.randomUUID());
        when(deliveryRepository.findActiveAppDelivery(spaceId, memberId, LocalDateTime.now(clock)))
                .thenReturn(Optional.of(active));

        CreateSpaceEnquiryRequest request = new CreateSpaceEnquiryRequest();
        request.setDeliveryChannel(EnquiryDeliveryChannel.APP);

        assertThatThrownBy(() -> service.create(memberId, spaceId, request, InquiryClientChannel.WEB))
                .isInstanceOf(AlreadyDeliveredException.class)
                .satisfies(ex -> {
                    AlreadyDeliveredException ade = (AlreadyDeliveredException) ex;
                    assertThat(ade.getChannel()).isEqualTo(EnquiryDeliveryChannel.APP);
                    assertThat(ade.getDeliveredAt()).isEqualTo(deliveredAt);
                });

        verify(inquiryAccessService, never()).consumeAfterSuccessfulCreate(any(), any(), any(), any());
        verify(enquiryRepository, never()).saveAndFlush(any());
    }

    @Test
    void activeAppDoesNotBlockEmailDelivery() {
        stubDiscoverableMember();
        SpaceEnquiryEntity shared = sharedEnquiry();
        when(enquiryRepository.findByIdAndRequesterUserId(shared.getId(), memberId))
                .thenReturn(Optional.of(shared));
        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        OwnerContactResponse contact = shareableContact();
        when(ownerContactResolver.resolve(space)).thenReturn(contact);
        when(ownerContactResolver.hasShareableContact(contact)).thenReturn(true);

        SpaceEnquiryDeliveryEntity app = SpaceEnquiryDeliveryEntity.builder()
                .enquiryId(shared.getId())
                .spaceId(spaceId)
                .requesterUserId(memberId)
                .deliveryChannel(EnquiryDeliveryChannel.APP)
                .deliveredAt(LocalDateTime.now(clock).minusDays(2))
                .expiresAt(LocalDateTime.now(clock).plusDays(10))
                .build();
        when(deliveryRepository.findActiveAppDelivery(spaceId, memberId, LocalDateTime.now(clock)))
                .thenReturn(Optional.of(app));

        SpaceEnquiryResponse response =
                service.deliverContactByEmail(memberId, shared.getId(), "John@Example.com");

        assertThat(response.getDeliveryChannel()).isEqualTo(EnquiryDeliveryChannel.EMAIL);
        assertThat(response.getRequesterEmail()).isEqualTo("john@example.com");
        verify(inquiryAccessService).authorizeNewEnquiry(memberId, InquiryClientChannel.WEB);
        verify(inquiryAccessService).consumeAfterSuccessfulCreate(eq(memberId), eq(InquiryClientChannel.WEB), any(), eq(shared.getId()));
        verify(emailService).send(any());
    }

    @Test
    void activeEmailBlocksSameNormalizedAddressWithoutConsume() {
        stubDiscoverableMember();
        SpaceEnquiryEntity shared = sharedEnquiry();
        when(enquiryRepository.findByIdAndRequesterUserId(shared.getId(), memberId))
                .thenReturn(Optional.of(shared));
        LocalDateTime deliveredAt = LocalDateTime.now(clock).minusHours(3);
        SpaceEnquiryDeliveryEntity emailDelivery = SpaceEnquiryDeliveryEntity.builder()
                .enquiryId(shared.getId())
                .spaceId(spaceId)
                .requesterUserId(memberId)
                .deliveryChannel(EnquiryDeliveryChannel.EMAIL)
                .recipientEmail("john@gmail.com")
                .deliveredAt(deliveredAt)
                .expiresAt(LocalDateTime.now(clock).plusDays(5))
                .build();
        when(deliveryRepository.findActiveEmailDelivery(
                        spaceId, memberId, "john@gmail.com", LocalDateTime.now(clock)))
                .thenReturn(Optional.of(emailDelivery));

        assertThatThrownBy(
                        () -> service.deliverContactByEmail(memberId, shared.getId(), "  JOHN@GMAIL.COM "))
                .isInstanceOf(AlreadyDeliveredException.class)
                .satisfies(ex -> {
                    AlreadyDeliveredException ade = (AlreadyDeliveredException) ex;
                    assertThat(ade.getChannel()).isEqualTo(EnquiryDeliveryChannel.EMAIL);
                    assertThat(ade.getRecipientEmail()).isEqualTo("john@gmail.com");
                    assertThat(ade.getDeliveredAt()).isEqualTo(deliveredAt);
                });

        verify(emailService, never()).send(any());
        verify(inquiryAccessService, never()).consumeAfterSuccessfulCreate(any(), any(), any(), any());
    }

    @Test
    void differentEmailAddressIsAllowedWhileOtherEmailActive() {
        stubDiscoverableMember();
        SpaceEnquiryEntity shared = sharedEnquiry();
        when(enquiryRepository.findByIdAndRequesterUserId(shared.getId(), memberId))
                .thenReturn(Optional.of(shared));
        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        OwnerContactResponse contact = shareableContact();
        when(ownerContactResolver.resolve(space)).thenReturn(contact);
        when(ownerContactResolver.hasShareableContact(contact)).thenReturn(true);
        when(deliveryRepository.existsBySpaceIdAndRequesterUserIdAndDeliveryChannel(
                        spaceId, memberId, EnquiryDeliveryChannel.EMAIL))
                .thenReturn(true);

        SpaceEnquiryResponse response =
                service.deliverContactByEmail(memberId, shared.getId(), "other@gmail.com");

        assertThat(response.getDeliveryChannel()).isEqualTo(EnquiryDeliveryChannel.EMAIL);
        assertThat(response.getRequesterEmail()).isEqualTo("other@gmail.com");
        verify(inquiryAccessService).consumeAfterSuccessfulCreate(any(), any(), any(), any());
    }

    @Test
    void expiredAppDeliveryAllowsAppAgain() {
        stubDiscoverableMember();
        SpaceEnquiryEntity shared = sharedEnquiry();
        when(enquiryRepository.findBySpaceIdAndRequesterUserIdAndStatus(
                        spaceId, memberId, SpaceEnquiryStatus.PENDING))
                .thenReturn(Optional.empty());
        when(enquiryRepository.findBySpaceIdAndRequesterUserIdAndStatus(
                        spaceId, memberId, SpaceEnquiryStatus.SHARED))
                .thenReturn(Optional.of(shared));
        when(enquiryRepository.findById(shared.getId())).thenReturn(Optional.of(shared));
        Mockito.lenient().when(ownerContactResolver.resolveOrEmpty(space)).thenReturn(shareableContact());

        SpaceEnquiryDeliveryEntity expired = SpaceEnquiryDeliveryEntity.builder()
                .enquiryId(shared.getId())
                .spaceId(spaceId)
                .requesterUserId(memberId)
                .deliveryChannel(EnquiryDeliveryChannel.APP)
                .deliveredAt(LocalDateTime.now(clock).minusDays(40))
                .expiresAt(LocalDateTime.now(clock).minusDays(1))
                .build();
        expired.setId(UUID.randomUUID());
        when(deliveryRepository.findAppDeliveryForUpdate(spaceId, memberId, EnquiryDeliveryChannel.APP))
                .thenReturn(Optional.of(expired));

        CreateSpaceEnquiryRequest request = new CreateSpaceEnquiryRequest();
        request.setDeliveryChannel(EnquiryDeliveryChannel.APP);

        SpaceEnquiryResponse response =
                service.create(memberId, spaceId, request, InquiryClientChannel.WEB);

        assertThat(response.getDeliveryChannel()).isEqualTo(EnquiryDeliveryChannel.APP);
        verify(inquiryAccessService).authorizeNewEnquiry(memberId, InquiryClientChannel.WEB);
        verify(inquiryAccessService).consumeAfterSuccessfulCreate(any(), any(), any(), eq(shared.getId()));
        verify(deliveryRepository).save(expired);
        assertThat(expired.getExpiresAt()).isEqualTo(shared.getExpiresAt());
    }

    @Test
    void activeEmailDoesNotBlockAppDelivery() {
        stubDiscoverableMember();
        SpaceEnquiryEntity shared = sharedEnquiry();
        when(enquiryRepository.findBySpaceIdAndRequesterUserIdAndStatus(
                        spaceId, memberId, SpaceEnquiryStatus.PENDING))
                .thenReturn(Optional.empty());
        when(enquiryRepository.findBySpaceIdAndRequesterUserIdAndStatus(
                        spaceId, memberId, SpaceEnquiryStatus.SHARED))
                .thenReturn(Optional.of(shared));
        when(enquiryRepository.findById(shared.getId())).thenReturn(Optional.of(shared));
        Mockito.lenient().when(ownerContactResolver.resolveOrEmpty(space)).thenReturn(shareableContact());

        CreateSpaceEnquiryRequest request = new CreateSpaceEnquiryRequest();
        request.setDeliveryChannel(EnquiryDeliveryChannel.APP);

        SpaceEnquiryResponse response =
                service.create(memberId, spaceId, request, InquiryClientChannel.WEB);

        assertThat(response.getDeliveryChannel()).isEqualTo(EnquiryDeliveryChannel.APP);
        verify(deliveryRepository).saveAndFlush(any());
    }

    private void stubDiscoverableMember() {
        Mockito.lenient()
                .when(userRepository.findByIdAndIsActiveTrue(memberId))
                .thenReturn(Optional.of(member));
        Mockito.lenient()
                .when(spaceRepository.findByIdAndIsActiveTrueAndDiscoverableTrue(spaceId))
                .thenReturn(Optional.of(space));
        Mockito.lenient()
                .when(spaceRepository.existsByIdAndOwnerIdAndIsActiveTrue(spaceId, memberId))
                .thenReturn(false);
    }

    private SpaceEnquiryEntity stubNewSharedEnquiry() {
        when(enquiryRepository.findBySpaceIdAndRequesterUserIdAndStatus(
                        spaceId, memberId, SpaceEnquiryStatus.PENDING))
                .thenReturn(Optional.empty());
        when(enquiryRepository.findBySpaceIdAndRequesterUserIdAndStatus(
                        spaceId, memberId, SpaceEnquiryStatus.SHARED))
                .thenReturn(Optional.empty());
        when(enquiryAutoSharePolicy.evaluate(any(), any()))
                .thenReturn(EnquiryAutoShareDecision.allow(shareableContact()));
        Mockito.lenient().when(ownerContactResolver.resolveOrEmpty(any())).thenReturn(shareableContact());
        SpaceEnquiryEntity[] holder = new SpaceEnquiryEntity[1];
        when(enquiryRepository.saveAndFlush(any())).thenAnswer(inv -> {
            SpaceEnquiryEntity entity = inv.getArgument(0);
            entity.setId(UUID.randomUUID());
            holder[0] = entity;
            when(enquiryRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
            return entity;
        });
        when(enquiryRepository.save(any())).thenAnswer(inv -> {
            SpaceEnquiryEntity entity = inv.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            holder[0] = entity;
            when(enquiryRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
            return entity;
        });
        return SpaceEnquiryEntity.builder()
                .spaceId(spaceId)
                .spaceNameSnapshot(space.getName())
                .requesterUserId(memberId)
                .requesterNameSnapshot(member.getFullName())
                .requesterEmail(member.getEmail())
                .requesterType(EnquiryRequesterType.MEMBER)
                .clientChannel(InquiryClientChannel.WEB)
                .status(SpaceEnquiryStatus.SHARED)
                .requestedAt(LocalDateTime.now(clock))
                .expiresAt(LocalDateTime.now(clock).plusDays(30))
                .build();
    }

    private SpaceEnquiryEntity sharedEnquiry() {
        SpaceEnquiryEntity entity = SpaceEnquiryEntity.builder()
                .spaceId(spaceId)
                .spaceNameSnapshot(space.getName())
                .requesterUserId(memberId)
                .requesterNameSnapshot(member.getFullName())
                .requesterEmail(member.getEmail())
                .requesterType(EnquiryRequesterType.MEMBER)
                .clientChannel(InquiryClientChannel.WEB)
                .status(SpaceEnquiryStatus.SHARED)
                .requestedAt(LocalDateTime.now(clock).minusDays(1))
                .expiresAt(LocalDateTime.now(clock).plusDays(29))
                .sharedAt(LocalDateTime.now(clock).minusDays(1))
                .build();
        entity.setId(UUID.randomUUID());
        return entity;
    }

    private static OwnerContactResponse shareableContact() {
        return OwnerContactResponse.builder()
                .ownerName("Owner")
                .mobileNumber("9991110001")
                .available(true)
                .build();
    }
}
