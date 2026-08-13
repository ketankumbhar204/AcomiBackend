package com.acomi.acomi_backend.notification.application.service;

import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.notification.api.dto.response.NotificationListResponse;
import com.acomi.acomi_backend.notification.api.dto.response.NotificationResponse;
import com.acomi.acomi_backend.notification.application.port.in.PublishNotificationCommand;
import com.acomi.acomi_backend.notification.domain.model.NotificationCategory;
import com.acomi.acomi_backend.notification.domain.model.NotificationEntityType;
import com.acomi.acomi_backend.notification.domain.model.NotificationStatus;
import com.acomi.acomi_backend.notification.domain.model.NotificationType;
import com.acomi.acomi_backend.notification.infrastructure.persistence.entity.SpaceNotificationEntity;
import com.acomi.acomi_backend.notification.infrastructure.persistence.repository.SpaceNotificationRepository;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Set<NotificationStatus> OPEN_STATUSES =
            EnumSet.of(NotificationStatus.UNREAD, NotificationStatus.READ);

    private final SpaceNotificationRepository notificationRepository;

    @Transactional
    public NotificationResponse publish(PublishNotificationCommand command) {
        String dedupeKey = command.getDedupeKey() != null && !command.getDedupeKey().isBlank()
                ? command.getDedupeKey()
                : defaultDedupeKey(command);

        Optional<SpaceNotificationEntity> existing = notificationRepository
                .findBySpaceIdAndDedupeKeyAndStatusIn(command.getSpaceId(), dedupeKey, OPEN_STATUSES);

        if (existing.isPresent()) {
            SpaceNotificationEntity open = existing.get();
            open.setTitle(command.getTitle());
            open.setMessage(command.getMessage());
            open.setActionLabel(command.getActionLabel());
            open.setActionRoute(command.getActionRoute());
            open.setPriority(command.getPriority());
            open.setActorId(command.getActorId());
            return NotificationResponse.from(notificationRepository.save(open));
        }

        SpaceNotificationEntity entity = SpaceNotificationEntity.builder()
                .spaceId(command.getSpaceId())
                .organizationId(command.getOrganizationId())
                .userId(command.getUserId())
                .actorId(command.getActorId())
                .entityType(command.getEntityType())
                .entityId(command.getEntityId())
                .notificationType(command.getNotificationType())
                .category(command.getCategory())
                .priority(command.getPriority())
                .title(command.getTitle())
                .message(command.getMessage())
                .actionLabel(command.getActionLabel())
                .actionRoute(command.getActionRoute())
                .status(NotificationStatus.UNREAD)
                .deliveryChannels(joinChannels(command.getDeliveryChannels()))
                .dedupeKey(dedupeKey)
                .build();

        return NotificationResponse.from(notificationRepository.save(entity));
    }

    @Transactional
    public void resolveOpenTypesForUser(UUID spaceId, UUID userId, Set<NotificationType> types) {
        if (types == null || types.isEmpty()) {
            return;
        }
        List<SpaceNotificationEntity> open =
                notificationRepository.findBySpaceIdAndUserIdAndStatusInOrderByCreatedAtDesc(
                        spaceId, userId, OPEN_STATUSES);
        LocalDateTime now = LocalDateTime.now();
        List<SpaceNotificationEntity> toResolve = open.stream()
                .filter(n -> types.contains(n.getNotificationType()))
                .toList();
        for (SpaceNotificationEntity notification : toResolve) {
            notification.setStatus(NotificationStatus.RESOLVED);
            notification.setResolvedAt(now);
            if (notification.getReadAt() == null) {
                notification.setReadAt(now);
            }
        }
        if (!toResolve.isEmpty()) {
            notificationRepository.saveAll(toResolve);
        }
    }

    @Transactional
    public void resolveOpenForEntity(
            UUID spaceId, NotificationEntityType entityType, UUID entityId, NotificationType type) {
        List<SpaceNotificationEntity> open = notificationRepository
                .findBySpaceIdAndEntityTypeAndEntityIdAndStatusIn(spaceId, entityType, entityId, OPEN_STATUSES)
                .stream()
                .filter(n -> type == null || n.getNotificationType() == type)
                .toList();
        LocalDateTime now = LocalDateTime.now();
        for (SpaceNotificationEntity notification : open) {
            notification.setStatus(NotificationStatus.RESOLVED);
            notification.setResolvedAt(now);
        }
        notificationRepository.saveAll(open);
    }

    /** Resolve open notifications for an entity, limited to specific recipients (e.g. managers only). */
    @Transactional
    public void resolveOpenForEntityUsers(
            UUID spaceId,
            NotificationEntityType entityType,
            UUID entityId,
            NotificationType type,
            java.util.Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        java.util.Set<UUID> recipients = new java.util.HashSet<>(userIds);
        List<SpaceNotificationEntity> open = notificationRepository
                .findBySpaceIdAndEntityTypeAndEntityIdAndStatusIn(spaceId, entityType, entityId, OPEN_STATUSES)
                .stream()
                .filter(n -> type == null || n.getNotificationType() == type)
                .filter(n -> recipients.contains(n.getUserId()))
                .toList();
        LocalDateTime now = LocalDateTime.now();
        for (SpaceNotificationEntity notification : open) {
            notification.setStatus(NotificationStatus.RESOLVED);
            notification.setResolvedAt(now);
            if (notification.getReadAt() == null) {
                notification.setReadAt(now);
            }
        }
        if (!open.isEmpty()) {
            notificationRepository.saveAll(open);
        }
    }

    @Transactional
    public void resolveOpenByType(UUID spaceId, NotificationType type) {
        List<SpaceNotificationEntity> open =
                notificationRepository.findBySpaceIdAndNotificationTypeAndStatusIn(spaceId, type, OPEN_STATUSES);
        LocalDateTime now = LocalDateTime.now();
        for (SpaceNotificationEntity notification : open) {
            notification.setStatus(NotificationStatus.RESOLVED);
            notification.setResolvedAt(now);
        }
        notificationRepository.saveAll(open);
    }

    @Transactional
    public NotificationResponse markRead(UUID spaceId, UUID notificationId, UUID userId) {
        SpaceNotificationEntity entity = loadOwned(spaceId, notificationId, userId);
        if (entity.getStatus() == NotificationStatus.UNREAD) {
            entity.setStatus(NotificationStatus.READ);
            entity.setReadAt(LocalDateTime.now());
            notificationRepository.save(entity);
        }
        return NotificationResponse.from(entity);
    }

    @Transactional
    public NotificationResponse resolve(UUID spaceId, UUID notificationId, UUID userId) {
        SpaceNotificationEntity entity = loadOwned(spaceId, notificationId, userId);
        if (entity.getStatus() != NotificationStatus.RESOLVED
                && entity.getStatus() != NotificationStatus.DISMISSED) {
            entity.setStatus(NotificationStatus.RESOLVED);
            entity.setResolvedAt(LocalDateTime.now());
            if (entity.getReadAt() == null) {
                entity.setReadAt(entity.getResolvedAt());
            }
            notificationRepository.save(entity);
        }
        return NotificationResponse.from(entity);
    }

    @Transactional(readOnly = true)
    public NotificationListResponse listForUser(UUID spaceId, UUID userId, boolean actionableOnly) {
        List<SpaceNotificationEntity> entities;
        if (actionableOnly) {
            entities = notificationRepository.findActionable(
                    spaceId, userId, NotificationCategory.ACTION_REQUIRED, OPEN_STATUSES);
        } else {
            entities = notificationRepository.findBySpaceIdAndUserIdAndStatusInOrderByCreatedAtDesc(
                    spaceId, userId, EnumSet.of(NotificationStatus.UNREAD, NotificationStatus.READ));
        }
        long unread = entities.stream().filter(n -> n.getStatus() == NotificationStatus.UNREAD).count();
        return NotificationListResponse.builder()
                .notifications(entities.stream().map(NotificationResponse::from).toList())
                .unreadCount(unread)
                .build();
    }

    @Transactional(readOnly = true)
    public List<SpaceNotificationEntity> listOpenActions(UUID spaceId, UUID userId) {
        return notificationRepository.findActionable(
                spaceId, userId, NotificationCategory.ACTION_REQUIRED, OPEN_STATUSES);
    }

    private SpaceNotificationEntity loadOwned(UUID spaceId, UUID notificationId, UUID userId) {
        SpaceNotificationEntity entity = notificationRepository
                .findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", notificationId));
        if (!entity.getSpaceId().equals(spaceId) || !entity.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Notification", "id", notificationId);
        }
        return entity;
    }

    private static String defaultDedupeKey(PublishNotificationCommand command) {
        return command.getNotificationType()
                + ":"
                + (command.getEntityId() != null ? command.getEntityId() : "none")
                + ":"
                + command.getUserId();
    }

    private static String joinChannels(List<String> channels) {
        if (channels == null || channels.isEmpty()) {
            return "IN_APP";
        }
        return String.join(",", channels);
    }
}
