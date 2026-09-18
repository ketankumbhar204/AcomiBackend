package com.acomi.acomi_backend.notification.application.service;

import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.notification.api.dto.response.NotificationListResponse;
import com.acomi.acomi_backend.notification.api.dto.response.NotificationResponse;
import com.acomi.acomi_backend.notification.api.dto.response.UserNotificationListResponse;
import com.acomi.acomi_backend.notification.api.dto.response.UserNotificationResponse;
import com.acomi.acomi_backend.notification.application.event.NotificationCreatedEvent;
import com.acomi.acomi_backend.notification.application.port.in.PublishNotificationCommand;
import com.acomi.acomi_backend.notification.domain.model.NotificationCategory;
import com.acomi.acomi_backend.notification.domain.model.NotificationEntityType;
import com.acomi.acomi_backend.notification.domain.model.NotificationStatus;
import com.acomi.acomi_backend.notification.domain.model.NotificationType;
import com.acomi.acomi_backend.notification.infrastructure.persistence.entity.SpaceNotificationEntity;
import com.acomi.acomi_backend.notification.infrastructure.persistence.repository.SpaceNotificationRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int MAX_PAGE_SIZE = 50;

    private static final Set<NotificationStatus> OPEN_STATUSES =
            EnumSet.of(NotificationStatus.UNREAD, NotificationStatus.READ);

    public static final Set<NotificationType> REQUESTER_ENQUIRY_TYPES = EnumSet.of(
            NotificationType.CONTACT_ENQUIRY_SUBMITTED,
            NotificationType.CONTACT_ENQUIRY_SHARED,
            NotificationType.CONTACT_ENQUIRY_REJECTED,
            NotificationType.CONTACT_ENQUIRY_EXPIRED);

    public static final Set<NotificationType> ALL_ENQUIRY_TYPES = EnumSet.of(
            NotificationType.CONTACT_ENQUIRY,
            NotificationType.CONTACT_ENQUIRY_SUBMITTED,
            NotificationType.CONTACT_ENQUIRY_SHARED,
            NotificationType.CONTACT_ENQUIRY_REJECTED,
            NotificationType.CONTACT_ENQUIRY_EXPIRED);

    /** Inquiry credit notification types visible to the user (seeker). */
    public static final Set<NotificationType> INQUIRY_CREDIT_USER_TYPES = EnumSet.of(
            NotificationType.INQUIRY_CREDIT_PAYMENT_APPROVED,
            NotificationType.INQUIRY_CREDIT_PAYMENT_REJECTED);

    /** All inquiry credit notification types (including admin-only). */
    public static final Set<NotificationType> ALL_INQUIRY_CREDIT_TYPES = EnumSet.of(
            NotificationType.INQUIRY_CREDIT_PAYMENT_PENDING,
            NotificationType.INQUIRY_CREDIT_PAYMENT_APPROVED,
            NotificationType.INQUIRY_CREDIT_PAYMENT_REJECTED);

    private final SpaceNotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public NotificationResponse publish(PublishNotificationCommand command) {
        String dedupeKey = command.getDedupeKey() != null && !command.getDedupeKey().isBlank()
                ? command.getDedupeKey()
                : defaultDedupeKey(command);

        Optional<SpaceNotificationEntity> existing;
        if (command.getSpaceId() == null) {
            existing = notificationRepository.findBySpaceIdIsNullAndDedupeKeyAndStatusIn(
                    dedupeKey, OPEN_STATUSES);
        } else {
            existing = notificationRepository.findBySpaceIdAndDedupeKeyAndStatusIn(
                    command.getSpaceId(), dedupeKey, OPEN_STATUSES);
        }

        if (existing.isPresent()) {
            SpaceNotificationEntity open = existing.get();
            open.setTitle(command.getTitle());
            open.setMessage(command.getMessage());
            open.setActionLabel(command.getActionLabel());
            open.setActionRoute(command.getActionRoute());
            open.setPriority(command.getPriority());
            open.setActorId(command.getActorId());
            log.info(
                    "notification skipped channel=FCM reason=duplicate notificationId={} type={} userId={}",
                    open.getId(),
                    command.getNotificationType(),
                    command.getUserId());
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

        SpaceNotificationEntity saved = notificationRepository.save(entity);
        log.info(
                "notification created notificationId={} type={} userId={} spaceId={}",
                saved.getId(),
                saved.getNotificationType(),
                saved.getUserId(),
                saved.getSpaceId());
        eventPublisher.publishEvent(NotificationCreatedEvent.builder()
                .notificationId(saved.getId())
                .userId(saved.getUserId())
                .spaceId(saved.getSpaceId())
                .notificationType(saved.getNotificationType())
                .entityType(saved.getEntityType())
                .entityId(saved.getEntityId())
                .title(saved.getTitle())
                .body(saved.getMessage())
                .actionRoute(saved.getActionRoute())
                .build());
        return NotificationResponse.from(saved);
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
        entities = entities.stream()
                .filter(n -> !ALL_ENQUIRY_TYPES.contains(n.getNotificationType()))
                .toList();
        long unread = entities.stream().filter(n -> n.getStatus() == NotificationStatus.UNREAD).count();
        return NotificationListResponse.builder()
                .notifications(entities.stream().map(NotificationResponse::from).toList())
                .unreadCount(unread)
                .build();
    }

    @Transactional(readOnly = true)
    public NotificationListResponse listForAdminUser(UUID userId) {
        Set<NotificationType> adminTypes = EnumSet.of(
                NotificationType.CONTACT_ENQUIRY, NotificationType.INQUIRY_CREDIT_PAYMENT_PENDING);
        List<SpaceNotificationEntity> entities =
                notificationRepository.findByUserIdAndNotificationTypeInAndStatusInOrderByCreatedAtDesc(
                        userId,
                        adminTypes,
                        EnumSet.of(NotificationStatus.UNREAD, NotificationStatus.READ));
        long unread = notificationRepository.countByUserIdAndNotificationTypeInAndStatus(
                userId, adminTypes, NotificationStatus.UNREAD);
        return NotificationListResponse.builder()
                .notifications(entities.stream().map(NotificationResponse::from).toList())
                .unreadCount(unread)
                .build();
    }

    @Transactional(readOnly = true)
    public UserNotificationListResponse listForCurrentUser(UUID userId, Pageable pageable) {
        Pageable safe = safePage(pageable);
        Set<NotificationType> userTypes = EnumSet.copyOf(REQUESTER_ENQUIRY_TYPES);
        userTypes.addAll(INQUIRY_CREDIT_USER_TYPES);
        Page<SpaceNotificationEntity> page =
                notificationRepository.findByUserIdAndNotificationTypeInAndStatusIn(
                        userId,
                        userTypes,
                        EnumSet.of(NotificationStatus.UNREAD, NotificationStatus.READ),
                        safe);
        long unread = notificationRepository.countByUserIdAndNotificationTypeInAndStatus(
                userId, userTypes, NotificationStatus.UNREAD);
        return UserNotificationListResponse.builder()
                .notifications(page.getContent().stream().map(UserNotificationResponse::from).toList())
                .unreadCount(unread)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }

    @Transactional
    public NotificationResponse markReadForAdmin(UUID notificationId, UUID userId) {
        SpaceNotificationEntity entity = notificationRepository
                .findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", notificationId));
        if (entity.getNotificationType() != NotificationType.CONTACT_ENQUIRY
                && entity.getNotificationType() != NotificationType.INQUIRY_CREDIT_PAYMENT_PENDING) {
            throw new ResourceNotFoundException("Notification", "id", notificationId);
        }
        if (entity.getStatus() == NotificationStatus.UNREAD) {
            entity.setStatus(NotificationStatus.READ);
            entity.setReadAt(LocalDateTime.now());
            notificationRepository.save(entity);
        }
        return NotificationResponse.from(entity);
    }

    @Transactional
    public UserNotificationResponse markReadForCurrentUser(UUID notificationId, UUID userId) {
        SpaceNotificationEntity entity = notificationRepository
                .findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", notificationId));
        if (!REQUESTER_ENQUIRY_TYPES.contains(entity.getNotificationType())
                && !INQUIRY_CREDIT_USER_TYPES.contains(entity.getNotificationType())) {
            throw new ResourceNotFoundException("Notification", "id", notificationId);
        }
        if (entity.getStatus() == NotificationStatus.UNREAD) {
            entity.setStatus(NotificationStatus.READ);
            entity.setReadAt(LocalDateTime.now());
            notificationRepository.save(entity);
        }
        return UserNotificationResponse.from(entity);
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
        List<String> resolved = new ArrayList<>();
        if (channels != null) {
            for (String channel : channels) {
                if (channel != null && !channel.isBlank() && !resolved.contains(channel.trim())) {
                    resolved.add(channel.trim());
                }
            }
        }
        if (resolved.isEmpty()) {
            resolved.add("IN_APP");
        }
        if (!resolved.contains("PUSH")) {
            resolved.add("PUSH");
        }
        return String.join(",", resolved);
    }

    private static Pageable safePage(Pageable pageable) {
        int page = pageable != null ? Math.max(pageable.getPageNumber(), 0) : 0;
        int size = pageable != null ? pageable.getPageSize() : 20;
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(page, safeSize);
    }
}
