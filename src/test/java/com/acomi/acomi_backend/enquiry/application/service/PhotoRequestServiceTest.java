package com.acomi.acomi_backend.enquiry.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.enquiry.api.dto.response.OwnerContactResponse;
import com.acomi.acomi_backend.enquiry.api.dto.response.PhotoRequestResponse;
import com.acomi.acomi_backend.notification.api.dto.response.NotificationResponse;
import com.acomi.acomi_backend.notification.application.port.in.PublishNotificationCommand;
import com.acomi.acomi_backend.notification.application.service.NotificationService;
import com.acomi.acomi_backend.notification.domain.model.NotificationCategory;
import com.acomi.acomi_backend.notification.domain.model.NotificationEntityType;
import com.acomi.acomi_backend.notification.domain.model.NotificationPriority;
import com.acomi.acomi_backend.notification.domain.model.NotificationStatus;
import com.acomi.acomi_backend.notification.domain.model.NotificationType;
import com.acomi.acomi_backend.space.application.service.SpaceDiscoverService;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.user.domain.model.SystemRole;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PhotoRequestServiceTest {

    @Mock
    private SpaceDiscoverService spaceDiscoverService;

    @Mock
    private OwnerContactResolver ownerContactResolver;

    @Mock
    private EnquiryInboxAccess enquiryInboxAccess;

    @Mock
    private NotificationService notificationService;

    private PhotoRequestService service;
    private UUID requesterId;
    private UUID ownerId;
    private UUID spaceId;

    @BeforeEach
    void setUp() {
        service = new PhotoRequestService(
                spaceDiscoverService, ownerContactResolver, enquiryInboxAccess, notificationService);
        requesterId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        spaceId = UUID.randomUUID();
    }

    @Test
    void propertyRequestNotifiesTheOwnerAndReturnsWhatsAppDigits() {
        SpaceEntity space = space(SpaceType.PG, "Sri Sai Venkateshwara PG");
        UserEntity owner = user(ownerId, SystemRole.USER);
        when(spaceDiscoverService.requireDiscoverableSpace(spaceId)).thenReturn(space);
        when(enquiryInboxAccess.canReceive(requesterId, spaceId)).thenReturn(false);
        when(enquiryInboxAccess.managersOf(spaceId)).thenReturn(List.of(owner));
        when(ownerContactResolver.resolveOrEmpty(space)).thenReturn(contact("9876543210"));
        when(notificationService.hasOpenNotification(eq(spaceId), any())).thenReturn(false);
        when(notificationService.publish(any())).thenReturn(savedNotification(ownerId));

        PhotoRequestResponse response = service.request(requesterId, spaceId);

        assertThat(response.isPhoneAvailable()).isTrue();
        assertThat(response.getInternationalPhone()).isEqualTo("919876543210");
        assertThat(response.isNotificationPersisted()).isTrue();
        assertThat(response.isNotificationDuplicate()).isFalse();
        assertThat(response.isFcmQueued()).isTrue();

        PublishNotificationCommand command = capturePublish();
        assertThat(command.getUserId()).isEqualTo(ownerId);
        assertThat(command.getActorId()).isEqualTo(requesterId);
        assertThat(command.getNotificationType()).isEqualTo(NotificationType.PHOTO_REQUEST_RECEIVED);
        assertThat(command.getEntityType()).isEqualTo(NotificationEntityType.SPACE);
        assertThat(command.getEntityId()).isEqualTo(spaceId);
        assertThat(command.getActionRoute()).isEqualTo("FindAPlaceDetail");
        assertThat(command.getTitle()).isEqualTo("Photo request received");
        assertThat(command.getMessage())
                .isEqualTo(
                        "Someone interested in Sri Sai Venkateshwara PG has requested photos. Open ACOMI to review the request.");
        assertThat(command.getMessage()).doesNotContain("9876543210");
        assertThat(command.getDedupeKey()).contains(requesterId.toString()).contains(ownerId.toString());
    }

    @Test
    void messRequestNotifiesTheVendor() {
        SpaceEntity space = space(SpaceType.MESS, "Annapurna Mess");
        UserEntity vendor = user(ownerId, SystemRole.USER);
        when(spaceDiscoverService.requireDiscoverableSpace(spaceId)).thenReturn(space);
        when(enquiryInboxAccess.canReceive(requesterId, spaceId)).thenReturn(false);
        when(enquiryInboxAccess.managersOf(spaceId)).thenReturn(List.of(vendor));
        when(ownerContactResolver.resolveOrEmpty(space)).thenReturn(contact("8047622222"));
        when(notificationService.hasOpenNotification(eq(spaceId), any())).thenReturn(false);
        when(notificationService.publish(any())).thenReturn(savedNotification(ownerId));

        PhotoRequestResponse response = service.request(requesterId, spaceId);

        assertThat(response.getInternationalPhone()).isEqualTo("918047622222");
        assertThat(capturePublish().getMessage()).contains("Annapurna Mess");
        assertThat(capturePublish().getUserId()).isEqualTo(ownerId);
    }

    @Test
    void missingPhoneStillSavesTheNotification() {
        SpaceEntity space = space(SpaceType.PG, "Green PG");
        UserEntity owner = user(ownerId, SystemRole.USER);
        when(spaceDiscoverService.requireDiscoverableSpace(spaceId)).thenReturn(space);
        when(enquiryInboxAccess.canReceive(requesterId, spaceId)).thenReturn(false);
        when(enquiryInboxAccess.managersOf(spaceId)).thenReturn(List.of(owner));
        when(ownerContactResolver.resolveOrEmpty(space))
                .thenReturn(OwnerContactResponse.builder().available(false).build());
        when(notificationService.hasOpenNotification(eq(spaceId), any())).thenReturn(false);
        when(notificationService.publish(any())).thenReturn(savedNotification(ownerId));

        PhotoRequestResponse response = service.request(requesterId, spaceId);

        assertThat(response.isPhoneAvailable()).isFalse();
        assertThat(response.getInternationalPhone()).isNull();
        assertThat(response.isNotificationPersisted()).isTrue();
        assertThat(response.isFcmQueued()).isTrue();
    }

    @Test
    void retryDoesNotQueueAnotherPush() {
        SpaceEntity space = space(SpaceType.PG, "Green PG");
        UserEntity owner = user(ownerId, SystemRole.USER);
        when(spaceDiscoverService.requireDiscoverableSpace(spaceId)).thenReturn(space);
        when(enquiryInboxAccess.canReceive(requesterId, spaceId)).thenReturn(false);
        when(enquiryInboxAccess.managersOf(spaceId)).thenReturn(List.of(owner));
        when(ownerContactResolver.resolveOrEmpty(space)).thenReturn(contact("9876543210"));
        when(notificationService.hasOpenNotification(eq(spaceId), any())).thenReturn(true);
        when(notificationService.publish(any())).thenReturn(savedNotification(ownerId));

        PhotoRequestResponse response = service.request(requesterId, spaceId);

        assertThat(response.isNotificationPersisted()).isTrue();
        assertThat(response.isNotificationDuplicate()).isTrue();
        assertThat(response.isFcmQueued()).isFalse();
        assertThat(response.isPhoneAvailable()).isTrue();
    }

    @Test
    void notificationFailureStillReturnsThePhone() {
        SpaceEntity space = space(SpaceType.PG, "Green PG");
        UserEntity owner = user(ownerId, SystemRole.USER);
        when(spaceDiscoverService.requireDiscoverableSpace(spaceId)).thenReturn(space);
        when(enquiryInboxAccess.canReceive(requesterId, spaceId)).thenReturn(false);
        when(enquiryInboxAccess.managersOf(spaceId)).thenReturn(List.of(owner));
        when(ownerContactResolver.resolveOrEmpty(space)).thenReturn(contact("9876543210"));
        when(notificationService.hasOpenNotification(eq(spaceId), any())).thenReturn(false);
        when(notificationService.publish(any())).thenThrow(new IllegalStateException("db down"));

        PhotoRequestResponse response = service.request(requesterId, spaceId);

        assertThat(response.isNotificationPersisted()).isFalse();
        assertThat(response.isFcmQueued()).isFalse();
        assertThat(response.getInternationalPhone()).isEqualTo("919876543210");
    }

    @Test
    void doesNotNotifyTheRequesterOrAPlatformAdmin() {
        UserEntity admin = user(UUID.randomUUID(), SystemRole.ADMIN);
        UserEntity requester = user(requesterId, SystemRole.USER);
        SpaceEntity space = space(SpaceType.HOSTEL, "Hostel One");
        space.setOwner(admin);
        when(spaceDiscoverService.requireDiscoverableSpace(spaceId)).thenReturn(space);
        when(enquiryInboxAccess.canReceive(requesterId, spaceId)).thenReturn(false);
        when(enquiryInboxAccess.managersOf(spaceId)).thenReturn(List.of(admin, requester));
        when(ownerContactResolver.resolveOrEmpty(space)).thenReturn(contact("9876543210"));

        PhotoRequestResponse response = service.request(requesterId, spaceId);

        assertThat(response.isNotificationPersisted()).isFalse();
        assertThat(response.isPhoneAvailable()).isTrue();
        verify(notificationService, never()).publish(any());
    }

    @Test
    void ownerCannotRequestPhotosForTheirOwnListing() {
        SpaceEntity space = space(SpaceType.PG, "Own PG");
        space.setOwner(user(requesterId, SystemRole.USER));
        when(spaceDiscoverService.requireDiscoverableSpace(spaceId)).thenReturn(space);
        when(enquiryInboxAccess.canReceive(requesterId, spaceId)).thenReturn(true);

        assertThatThrownBy(() -> service.request(requesterId, spaceId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "PHOTO_REQUEST_OWN_LISTING");
        verify(notificationService, never()).publish(any());
        verify(ownerContactResolver, never()).resolveOrEmpty(any());
    }

    @Test
    void hiddenListingIsNotRequestable() {
        when(spaceDiscoverService.requireDiscoverableSpace(spaceId))
                .thenThrow(new ResourceNotFoundException("Space", "id", spaceId));

        assertThatThrownBy(() -> service.request(requesterId, spaceId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(notificationService, never()).publish(any());
    }

    private PublishNotificationCommand capturePublish() {
        ArgumentCaptor<PublishNotificationCommand> captor = ArgumentCaptor.forClass(PublishNotificationCommand.class);
        verify(notificationService).publish(captor.capture());
        return captor.getValue();
    }

    private SpaceEntity space(SpaceType type, String name) {
        SpaceEntity space = new SpaceEntity();
        space.setId(spaceId);
        space.setName(name);
        space.setType(type);
        space.setDiscoverable(true);
        space.setActive(true);
        space.setOwner(user(ownerId, SystemRole.USER));
        return space;
    }

    private static UserEntity user(UUID id, SystemRole role) {
        UserEntity user = UserEntity.builder()
                .mobileNumber("9000000000")
                .systemRole(role)
                .isActive(true)
                .build();
        user.setId(id);
        return user;
    }

    private NotificationResponse savedNotification(UUID userId) {
        return NotificationResponse.builder()
                .notificationId(UUID.randomUUID())
                .spaceId(spaceId)
                .userId(userId)
                .entityType(NotificationEntityType.SPACE)
                .entityId(spaceId)
                .notificationType(NotificationType.PHOTO_REQUEST_RECEIVED)
                .category(NotificationCategory.INFORMATION)
                .priority(NotificationPriority.MEDIUM)
                .title("Photo request received")
                .message("Someone interested in the listing has requested photos.")
                .status(NotificationStatus.UNREAD)
                .deliveryChannels(List.of("IN_APP", "PUSH"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private static OwnerContactResponse contact(String mobile) {
        return OwnerContactResponse.builder().mobileNumber(mobile).available(true).build();
    }
}
