package com.acomi.acomi_backend.enquiry.application.service;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.enquiry.api.dto.response.OwnerContactResponse;
import com.acomi.acomi_backend.enquiry.api.dto.response.PhotoRequestResponse;
import com.acomi.acomi_backend.enquiry.application.support.WhatsAppClickToChat;
import com.acomi.acomi_backend.notification.api.dto.response.NotificationResponse;
import com.acomi.acomi_backend.notification.application.port.in.PublishNotificationCommand;
import com.acomi.acomi_backend.notification.application.service.NotificationService;
import com.acomi.acomi_backend.notification.domain.model.NotificationCategory;
import com.acomi.acomi_backend.notification.domain.model.NotificationEntityType;
import com.acomi.acomi_backend.notification.domain.model.NotificationPriority;
import com.acomi.acomi_backend.notification.domain.model.NotificationType;
import com.acomi.acomi_backend.space.application.service.SpaceDiscoverService;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Records a listing photo request for the owner or vendor and returns the
 * authorized WhatsApp number. Does not create a contact enquiry, reveal the
 * number on the public listing, or consume inquiry credits.
 * WhatsApp itself is opened by the client. FCM is attempted only after a new
 * in-app notification commits, and a failure in either channel does not cancel the other.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoRequestService {

    static final String ACTION_ROUTE = "FindAPlaceDetail";

    private final SpaceDiscoverService spaceDiscoverService;
    private final OwnerContactResolver ownerContactResolver;
    private final EnquiryInboxAccess enquiryInboxAccess;
    private final NotificationService notificationService;

    public PhotoRequestResponse request(UUID requesterId, UUID spaceId) {
        SpaceEntity space = spaceDiscoverService.requireDiscoverableSpace(spaceId);
        if (ownsOrManages(requesterId, space)) {
            throw new BusinessException(
                    "PHOTO_REQUEST_OWN_LISTING",
                    "You cannot request photos for a listing you own or manage.",
                    HttpStatus.FORBIDDEN);
        }

        String phone = firstCallablePhone(ownerContactResolver.resolveOrEmpty(space));
        String listingName = listingName(space);
        boolean persisted = false;
        boolean anyNew = false;

        for (UserEntity recipient : recipients(space, requesterId)) {
            String dedupeKey = "PHOTO_REQUEST_RECEIVED:"
                    + space.getId()
                    + ":"
                    + requesterId
                    + ":"
                    + recipient.getId();
            boolean alreadyOpen = notificationService.hasOpenNotification(space.getId(), dedupeKey);
            try {
                NotificationResponse published = notificationService.publish(PublishNotificationCommand.builder()
                        .spaceId(space.getId())
                        .userId(recipient.getId())
                        .actorId(requesterId)
                        .entityType(NotificationEntityType.SPACE)
                        .entityId(space.getId())
                        .notificationType(NotificationType.PHOTO_REQUEST_RECEIVED)
                        .category(NotificationCategory.INFORMATION)
                        .priority(NotificationPriority.MEDIUM)
                        .title("Photo request received")
                        .message("Someone interested in "
                                + listingName
                                + " has requested photos. Open ACOMI to review the request.")
                        .actionLabel("View listing")
                        .actionRoute(ACTION_ROUTE)
                        .dedupeKey(dedupeKey)
                        .build());
                if (published != null) {
                    persisted = true;
                    if (!alreadyOpen) {
                        anyNew = true;
                    }
                }
            } catch (RuntimeException ex) {
                log.warn(
                        "photo request notification failed spaceId={} recipientId={} message={}",
                        space.getId(),
                        recipient.getId(),
                        ex.getMessage());
            }
        }

        boolean duplicate = persisted && !anyNew;
        log.info(
                "photo request spaceId={} requesterId={} persisted={} duplicate={} fcmQueued={} phoneAvailable={}",
                space.getId(),
                requesterId,
                persisted,
                duplicate,
                anyNew,
                phone != null);
        return PhotoRequestResponse.builder()
                .notificationPersisted(persisted)
                .notificationDuplicate(duplicate)
                .fcmQueued(anyNew)
                .phoneAvailable(phone != null)
                .internationalPhone(phone)
                .build();
    }

    private boolean ownsOrManages(UUID requesterId, SpaceEntity space) {
        if (requesterId == null) {
            return false;
        }
        if (enquiryInboxAccess.canReceive(requesterId, space.getId())) {
            return true;
        }
        UserEntity owner = space.getOwner();
        return owner != null && requesterId.equals(owner.getId());
    }

    private List<UserEntity> recipients(SpaceEntity space, UUID requesterId) {
        Map<UUID, UserEntity> people = new LinkedHashMap<>();
        for (UserEntity manager : enquiryInboxAccess.managersOf(space.getId())) {
            addRecipient(people, manager, requesterId);
        }
        UserEntity owner = space.getOwner();
        if (owner != null && owner.isLinkableOwner()) {
            addRecipient(people, owner, requesterId);
        }
        return List.copyOf(people.values());
    }

    private static void addRecipient(Map<UUID, UserEntity> people, UserEntity user, UUID requesterId) {
        if (user == null || user.getId() == null || !user.isActive() || user.isPlatformAdmin()) {
            return;
        }
        if (requesterId != null && requesterId.equals(user.getId())) {
            return;
        }
        people.putIfAbsent(user.getId(), user);
    }

    private static String listingName(SpaceEntity space) {
        String name = space.getName();
        if (name == null || name.isBlank()) {
            return "this listing";
        }
        return name.trim();
    }

    private static String firstCallablePhone(OwnerContactResponse contact) {
        if (contact == null) {
            return null;
        }
        for (String raw : new String[] {
            contact.getMobileNumber(), contact.getAlternateMobileNumber(), contact.getAdditionalMobileNumber()
        }) {
            String digits = WhatsAppClickToChat.digits(raw);
            if (digits != null) {
                return digits;
            }
        }
        return null;
    }
}
