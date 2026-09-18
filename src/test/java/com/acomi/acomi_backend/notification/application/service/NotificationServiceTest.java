package com.acomi.acomi_backend.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.notification.api.dto.response.NotificationListResponse;
import com.acomi.acomi_backend.notification.api.dto.response.UserNotificationListResponse;
import com.acomi.acomi_backend.notification.api.dto.response.UserNotificationResponse;
import com.acomi.acomi_backend.notification.domain.model.NotificationCategory;
import com.acomi.acomi_backend.notification.domain.model.NotificationEntityType;
import com.acomi.acomi_backend.notification.domain.model.NotificationPriority;
import com.acomi.acomi_backend.notification.domain.model.NotificationStatus;
import com.acomi.acomi_backend.notification.domain.model.NotificationType;
import com.acomi.acomi_backend.notification.infrastructure.persistence.entity.SpaceNotificationEntity;
import com.acomi.acomi_backend.notification.infrastructure.persistence.repository.SpaceNotificationRepository;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private SpaceNotificationRepository notificationRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private NotificationService service;
    private UUID userA;
    private UUID userB;
    private UUID spaceId;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, eventPublisher);
        userA = UUID.randomUUID();
        userB = UUID.randomUUID();
        spaceId = UUID.randomUUID();
    }

    private static EnumSet<NotificationType> currentUserInboxTypes() {
        EnumSet<NotificationType> types = EnumSet.copyOf(NotificationService.REQUESTER_ENQUIRY_TYPES);
        types.addAll(NotificationService.INQUIRY_CREDIT_USER_TYPES);
        return types;
    }

    private static EnumSet<NotificationType> adminInboxTypes() {
        return EnumSet.of(
                NotificationType.CONTACT_ENQUIRY, NotificationType.INQUIRY_CREDIT_PAYMENT_PENDING);
    }

    @Test
    void requesterInboxDoesNotIncludeAdminEnquiryNotifications() {
        SpaceNotificationEntity shared = notification(
                userA, NotificationType.CONTACT_ENQUIRY_SHARED, NotificationStatus.UNREAD);
        when(notificationRepository.findByUserIdAndNotificationTypeInAndStatusIn(
                        userA,
                        currentUserInboxTypes(),
                        EnumSet.of(NotificationStatus.UNREAD, NotificationStatus.READ),
                        PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(shared), PageRequest.of(0, 20), 1));
        when(notificationRepository.countByUserIdAndNotificationTypeInAndStatus(
                        userA, currentUserInboxTypes(), NotificationStatus.UNREAD))
                .thenReturn(1L);

        UserNotificationListResponse response = service.listForCurrentUser(userA, PageRequest.of(0, 20));

        assertThat(response.getNotifications()).hasSize(1);
        assertThat(response.getNotifications().get(0).getNotificationType())
                .isEqualTo(NotificationType.CONTACT_ENQUIRY_SHARED);
        assertThat(response.getUnreadCount()).isEqualTo(1);
        assertThat(response.getNotifications().get(0).getMessage()).doesNotContain("999111");
    }

    @Test
    void adminInboxContainsOnlyContactEnquiryType() {
        SpaceNotificationEntity adminNote =
                notification(userA, NotificationType.CONTACT_ENQUIRY, NotificationStatus.UNREAD);
        when(notificationRepository.findByUserIdAndNotificationTypeInAndStatusInOrderByCreatedAtDesc(
                        userA,
                        adminInboxTypes(),
                        EnumSet.of(NotificationStatus.UNREAD, NotificationStatus.READ)))
                .thenReturn(List.of(adminNote));
        when(notificationRepository.countByUserIdAndNotificationTypeInAndStatus(
                        userA, adminInboxTypes(), NotificationStatus.UNREAD))
                .thenReturn(1L);

        NotificationListResponse response = service.listForAdminUser(userA);

        assertThat(response.getNotifications()).hasSize(1);
        assertThat(response.getNotifications().get(0).getNotificationType())
                .isEqualTo(NotificationType.CONTACT_ENQUIRY);
    }

    @Test
    void userCannotMarkAnotherUsersNotificationRead() {
        UUID notificationId = UUID.randomUUID();
        when(notificationRepository.findByIdAndUserId(notificationId, userA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markReadForCurrentUser(notificationId, userA))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void requesterCannotMarkAdminNotificationThroughUserApi() {
        UUID notificationId = UUID.randomUUID();
        SpaceNotificationEntity adminNote =
                notification(userA, NotificationType.CONTACT_ENQUIRY, NotificationStatus.UNREAD);
        adminNote.setId(notificationId);
        when(notificationRepository.findByIdAndUserId(notificationId, userA)).thenReturn(Optional.of(adminNote));

        assertThatThrownBy(() -> service.markReadForCurrentUser(notificationId, userA))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void markReadForCurrentUserPersistsReadState() {
        UUID notificationId = UUID.randomUUID();
        SpaceNotificationEntity shared =
                notification(userA, NotificationType.CONTACT_ENQUIRY_SHARED, NotificationStatus.UNREAD);
        shared.setId(notificationId);
        when(notificationRepository.findByIdAndUserId(notificationId, userA)).thenReturn(Optional.of(shared));
        when(notificationRepository.save(shared)).thenReturn(shared);

        UserNotificationResponse response = service.markReadForCurrentUser(notificationId, userA);

        assertThat(response.isRead()).isTrue();
        assertThat(shared.getStatus()).isEqualTo(NotificationStatus.READ);
        verify(notificationRepository).save(shared);
    }

    @Test
    void userBNotificationIsNotReturnedForUserA() {
        when(notificationRepository.findByUserIdAndNotificationTypeInAndStatusIn(
                        userA,
                        currentUserInboxTypes(),
                        EnumSet.of(NotificationStatus.UNREAD, NotificationStatus.READ),
                        PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        when(notificationRepository.countByUserIdAndNotificationTypeInAndStatus(
                        userA, currentUserInboxTypes(), NotificationStatus.UNREAD))
                .thenReturn(0L);

        UserNotificationListResponse response = service.listForCurrentUser(userA, PageRequest.of(0, 20));

        assertThat(response.getNotifications()).isEmpty();
        assertThat(response.getUnreadCount()).isZero();
    }

    @Test
    void publishPlatformNotificationWithNullSpaceIdUsesNullSpaceDedupePath() {
        when(notificationRepository.findBySpaceIdIsNullAndDedupeKeyAndStatusIn(
                        org.mockito.ArgumentMatchers.eq("platform-key"),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.empty());
        when(notificationRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    SpaceNotificationEntity entity = invocation.getArgument(0);
                    entity.setId(UUID.randomUUID());
                    return entity;
                });

        var command = com.acomi.acomi_backend.notification.application.port.in.PublishNotificationCommand.builder()
                .spaceId(null)
                .userId(userA)
                .entityType(NotificationEntityType.INQUIRY_CREDIT_PURCHASE)
                .entityId(UUID.randomUUID())
                .notificationType(NotificationType.INQUIRY_CREDIT_PAYMENT_PENDING)
                .category(NotificationCategory.ACTION_REQUIRED)
                .priority(NotificationPriority.HIGH)
                .title("New inquiry credit purchase request")
                .message("Review payment")
                .dedupeKey("platform-key")
                .build();

        var response = service.publish(command);

        assertThat(response.getNotificationId()).isNotNull();
        assertThat(response.getSpaceId()).isNull();
        verify(notificationRepository)
                .findBySpaceIdIsNullAndDedupeKeyAndStatusIn(
                        org.mockito.ArgumentMatchers.eq("platform-key"),
                        org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(notificationRepository, org.mockito.Mockito.never())
                .findBySpaceIdAndDedupeKeyAndStatusIn(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void publishNewNotificationEmitsAfterCommitEvent() {
        when(notificationRepository.findBySpaceIdAndDedupeKeyAndStatusIn(
                        org.mockito.ArgumentMatchers.eq(spaceId),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.empty());
        when(notificationRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    SpaceNotificationEntity entity = invocation.getArgument(0);
                    entity.setId(UUID.randomUUID());
                    return entity;
                });

        var command = com.acomi.acomi_backend.notification.application.port.in.PublishNotificationCommand.builder()
                .spaceId(spaceId)
                .userId(userA)
                .entityType(NotificationEntityType.DAILY_MENU)
                .entityId(UUID.randomUUID())
                .notificationType(NotificationType.MENU_PUBLISHED)
                .category(NotificationCategory.INFORMATION)
                .priority(NotificationPriority.MEDIUM)
                .title("Today's menu is ready")
                .message("Check today's menu for Sunrise PG.")
                .dedupeKey("INFO:MENU_PUBLISHED:" + UUID.randomUUID())
                .build();

        var response = service.publish(command);

        assertThat(response.getNotificationId()).isNotNull();
        org.mockito.Mockito.verify(eventPublisher)
                .publishEvent(org.mockito.ArgumentMatchers.any(
                        com.acomi.acomi_backend.notification.application.event.NotificationCreatedEvent.class));
    }

    @Test
    void publishDuplicateDoesNotEmitPushEvent() {
        SpaceNotificationEntity existing =
                notification(userA, NotificationType.MENU_PUBLISHED, NotificationStatus.UNREAD);
        when(notificationRepository.findBySpaceIdAndDedupeKeyAndStatusIn(
                        org.mockito.ArgumentMatchers.eq(spaceId),
                        org.mockito.ArgumentMatchers.eq("dup-key"),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.of(existing));
        when(notificationRepository.save(existing)).thenReturn(existing);

        var command = com.acomi.acomi_backend.notification.application.port.in.PublishNotificationCommand.builder()
                .spaceId(spaceId)
                .userId(userA)
                .entityType(NotificationEntityType.DAILY_MENU)
                .notificationType(NotificationType.MENU_PUBLISHED)
                .category(NotificationCategory.INFORMATION)
                .priority(NotificationPriority.MEDIUM)
                .title("Today's menu is ready")
                .message("Check today's menu for Sunrise PG.")
                .dedupeKey("dup-key")
                .build();

        service.publish(command);

        org.mockito.Mockito.verify(eventPublisher, org.mockito.Mockito.never())
                .publishEvent(org.mockito.ArgumentMatchers.any());
    }

    private SpaceNotificationEntity notification(
            UUID userId, NotificationType type, NotificationStatus status) {
        SpaceNotificationEntity entity = SpaceNotificationEntity.builder()
                .spaceId(spaceId)
                .userId(userId)
                .entityType(NotificationEntityType.SPACE_ENQUIRY)
                .entityId(UUID.randomUUID())
                .notificationType(type)
                .category(NotificationCategory.INFORMATION)
                .priority(NotificationPriority.MEDIUM)
                .title("Contact details shared")
                .message("ACOMI has shared the contact details for Sunrise PG. Check your email for the contact information.")
                .actionRoute("MyEnquiries")
                .status(status)
                .build();
        entity.setId(UUID.randomUUID());
        return entity;
    }
}
