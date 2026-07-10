package com.countin.countin_backend.notification.application.service;

import com.countin.countin_backend.member.domain.model.MembershipRole;
import com.countin.countin_backend.member.domain.model.MembershipStatus;
import com.countin.countin_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.countin.countin_backend.notification.application.port.in.PublishNotificationCommand;
import com.countin.countin_backend.notification.domain.model.NotificationCategory;
import com.countin.countin_backend.notification.domain.model.NotificationEntityType;
import com.countin.countin_backend.notification.domain.model.NotificationPriority;
import com.countin.countin_backend.notification.domain.model.NotificationType;
import com.countin.countin_backend.notification.infrastructure.persistence.entity.SpaceNotificationEntity;
import com.countin.countin_backend.payment.domain.model.SpacePaymentStatus;
import com.countin.countin_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.countin.countin_backend.payment.infrastructure.persistence.repository.SpacePaymentRepository;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps payment actionable notifications aligned with {@code SpacePayment} status.
 * Pending Actions never invent payment counts — they sync from the payment store.
 *
 * <p>Owner payment actions mirror the Payments "Pending Review" queue:
 * Submitted/Under Review → {@link NotificationType#PAYMENT_NEEDS_REVIEW};
 * Needs Update → {@link NotificationType#PAYMENT_NEEDS_UPDATE}.
 */
@Service
@RequiredArgsConstructor
public class PaymentNotificationSyncService {

    private static final Set<SpacePaymentStatus> REVIEW_STATUSES =
            EnumSet.of(SpacePaymentStatus.PROOF_UPLOADED, SpacePaymentStatus.UNDER_REVIEW);

    private static final Set<NotificationType> OWNER_PAYMENT_ACTION_TYPES = EnumSet.of(
            NotificationType.PAYMENT_NEEDS_REVIEW, NotificationType.PAYMENT_NEEDS_UPDATE);

    private final SpacePaymentRepository paymentRepository;
    private final SpaceMembershipRepository membershipRepository;
    private final NotificationService notificationService;

    @Transactional
    public void syncSpaceMonth(UUID spaceId, String month) {
        String resolvedMonth = month != null && !month.isBlank() ? month : YearMonth.now().toString();
        List<SpacePaymentEntity> payments = paymentRepository.findBySpaceIdAndMonth(spaceId, resolvedMonth);
        List<UUID> managerIds = managerUserIds(spaceId);
        Set<String> expectedOwnerKeys = new HashSet<>();
        Set<String> expectedTenantKeys = new HashSet<>();

        for (SpacePaymentEntity payment : payments) {
            SpacePaymentStatus status = payment.getPaymentStatus();
            UUID tenantUserId = linkedUserId(payment);

            if (REVIEW_STATUSES.contains(status)) {
                for (UUID managerId : managerIds) {
                    String key = actionDedupeKey(NotificationType.PAYMENT_NEEDS_REVIEW, payment.getId(), managerId);
                    expectedOwnerKeys.add(key);
                    publishOwnerAction(
                            spaceId,
                            managerId,
                            payment,
                            NotificationType.PAYMENT_NEEDS_REVIEW,
                            "Payment needs review",
                            memberLabel(payment) + " submitted proof for " + payment.getTitle(),
                            "Review Payment",
                            "Payments/pendingReview",
                            NotificationPriority.HIGH,
                            key);
                }
                notificationService.resolveOpenForEntity(
                        spaceId,
                        NotificationEntityType.PAYMENT,
                        payment.getId(),
                        NotificationType.PAYMENT_NEEDS_UPDATE);
                notificationService.resolveOpenForEntity(
                        spaceId,
                        NotificationEntityType.PAYMENT,
                        payment.getId(),
                        NotificationType.PAYMENT_UPDATE_REQUESTED);
            } else if (status == SpacePaymentStatus.UPDATE_REQUESTED) {
                for (UUID managerId : managerIds) {
                    String key = actionDedupeKey(NotificationType.PAYMENT_NEEDS_UPDATE, payment.getId(), managerId);
                    expectedOwnerKeys.add(key);
                    publishOwnerAction(
                            spaceId,
                            managerId,
                            payment,
                            NotificationType.PAYMENT_NEEDS_UPDATE,
                            "Payment needs update",
                            "Awaiting update from " + memberLabel(payment),
                            "View Payment",
                            "Payments/pendingReview",
                            NotificationPriority.MEDIUM,
                            key);
                }
                notificationService.resolveOpenForEntity(
                        spaceId,
                        NotificationEntityType.PAYMENT,
                        payment.getId(),
                        NotificationType.PAYMENT_NEEDS_REVIEW);
                if (tenantUserId != null) {
                    String tenantKey = actionDedupeKey(
                            NotificationType.PAYMENT_UPDATE_REQUESTED, payment.getId(), tenantUserId);
                    expectedTenantKeys.add(tenantKey);
                    publishTenantUpdateAction(
                            spaceId, tenantUserId, null, payment, payment.getRejectionReason(), tenantKey);
                }
            } else {
                notificationService.resolveOpenForEntity(
                        spaceId,
                        NotificationEntityType.PAYMENT,
                        payment.getId(),
                        NotificationType.PAYMENT_NEEDS_REVIEW);
                notificationService.resolveOpenForEntity(
                        spaceId,
                        NotificationEntityType.PAYMENT,
                        payment.getId(),
                        NotificationType.PAYMENT_NEEDS_UPDATE);
                notificationService.resolveOpenForEntity(
                        spaceId,
                        NotificationEntityType.PAYMENT,
                        payment.getId(),
                        NotificationType.PAYMENT_UPDATE_REQUESTED);
            }
        }

        resolveStaleOwnerPaymentActions(spaceId, managerIds, expectedOwnerKeys);
        resolveStaleTenantUpdateActions(spaceId, payments, expectedTenantKeys);
        for (SpacePaymentEntity payment : payments) {
            if (REVIEW_STATUSES.contains(payment.getPaymentStatus())
                    || payment.getPaymentStatus() == SpacePaymentStatus.UPDATE_REQUESTED) {
                resolveRedundantOwnerPaymentInfo(spaceId, payment.getId());
            }
        }
    }

    @Transactional
    public void onProofSubmitted(SpacePaymentEntity payment, UUID actorId, boolean resubmit) {
        UUID spaceId = payment.getSpace().getId();
        for (UUID managerId : managerUserIds(spaceId)) {
            String key = actionDedupeKey(NotificationType.PAYMENT_NEEDS_REVIEW, payment.getId(), managerId);
            // Owner Action Center already gets PAYMENT_NEEDS_REVIEW — do not also create a
            // redundant INFORMATION "Tenant updated payment" row (it inflated the bell badge).
            publishOwnerAction(
                    spaceId,
                    managerId,
                    payment,
                    NotificationType.PAYMENT_NEEDS_REVIEW,
                    resubmit ? "Tenant updated payment" : "Payment needs review",
                    resubmit
                            ? memberLabel(payment) + " · " + payment.getTitle()
                            : memberLabel(payment) + " submitted proof for " + payment.getTitle(),
                    "Review Payment",
                    "Payments/pendingReview",
                    NotificationPriority.HIGH,
                    key);
        }
        resolveRedundantOwnerPaymentInfo(spaceId, payment.getId());
        notificationService.resolveOpenForEntity(
                spaceId, NotificationEntityType.PAYMENT, payment.getId(), NotificationType.PAYMENT_NEEDS_UPDATE);
        notificationService.resolveOpenForEntity(
                spaceId,
                NotificationEntityType.PAYMENT,
                payment.getId(),
                NotificationType.PAYMENT_UPDATE_REQUESTED);

        UUID tenantUserId = linkedUserId(payment);
        if (tenantUserId != null) {
            notificationService.publish(PublishNotificationCommand.builder()
                    .spaceId(spaceId)
                    .userId(tenantUserId)
                    .actorId(actorId)
                    .entityType(NotificationEntityType.PAYMENT)
                    .entityId(payment.getId())
                    .notificationType(NotificationType.PAYMENT_SUBMITTED)
                    .category(NotificationCategory.INFORMATION)
                    .priority(NotificationPriority.LOW)
                    .title(resubmit ? "Payment updated" : "Payment submitted")
                    .message(payment.getTitle() + " is waiting for owner review")
                    .actionRoute("PaymentDetail")
                    .dedupeKey("INFO:" + NotificationType.PAYMENT_SUBMITTED + ":" + payment.getId() + ":"
                            + tenantUserId)
                    .build());
        }
    }

    /** Drop legacy owner INFO rows that duplicated PAYMENT_NEEDS_REVIEW / NEEDS_UPDATE. */
    private void resolveRedundantOwnerPaymentInfo(UUID spaceId, UUID paymentId) {
        notificationService.resolveOpenForEntityUsers(
                spaceId,
                NotificationEntityType.PAYMENT,
                paymentId,
                NotificationType.PAYMENT_SUBMITTED,
                managerUserIds(spaceId));
    }

    @Transactional
    public void onApproved(SpacePaymentEntity payment, UUID actorId) {
        UUID spaceId = payment.getSpace().getId();
        notificationService.resolveOpenForEntity(
                spaceId, NotificationEntityType.PAYMENT, payment.getId(), NotificationType.PAYMENT_NEEDS_REVIEW);
        notificationService.resolveOpenForEntity(
                spaceId, NotificationEntityType.PAYMENT, payment.getId(), NotificationType.PAYMENT_NEEDS_UPDATE);
        UUID tenantUserId = linkedUserId(payment);
        if (tenantUserId != null) {
            notificationService.resolveOpenForEntity(
                    spaceId,
                    NotificationEntityType.PAYMENT,
                    payment.getId(),
                    NotificationType.PAYMENT_UPDATE_REQUESTED);
            notificationService.publish(PublishNotificationCommand.builder()
                    .spaceId(spaceId)
                    .userId(tenantUserId)
                    .actorId(actorId)
                    .entityType(NotificationEntityType.PAYMENT)
                    .entityId(payment.getId())
                    .notificationType(NotificationType.PAYMENT_APPROVED)
                    .category(NotificationCategory.SUCCESS)
                    .priority(NotificationPriority.MEDIUM)
                    .title("Payment approved")
                    .message(payment.getTitle() + " was approved")
                    .dedupeKey("INFO:" + NotificationType.PAYMENT_APPROVED + ":" + payment.getId() + ":"
                            + tenantUserId)
                    .build());
        }
    }

    @Transactional
    public void onUpdateRequested(SpacePaymentEntity payment, UUID actorId, String message) {
        UUID spaceId = payment.getSpace().getId();
        notificationService.resolveOpenForEntity(
                spaceId, NotificationEntityType.PAYMENT, payment.getId(), NotificationType.PAYMENT_NEEDS_REVIEW);
        for (UUID managerId : managerUserIds(spaceId)) {
            String key = actionDedupeKey(NotificationType.PAYMENT_NEEDS_UPDATE, payment.getId(), managerId);
            publishOwnerAction(
                    spaceId,
                    managerId,
                    payment,
                    NotificationType.PAYMENT_NEEDS_UPDATE,
                    "Payment needs update",
                    "Awaiting update from " + memberLabel(payment),
                    "View Payment",
                    "Payments/pendingReview",
                    NotificationPriority.MEDIUM,
                    key);
        }
        UUID tenantUserId = linkedUserId(payment);
        if (tenantUserId != null) {
            String tenantKey =
                    actionDedupeKey(NotificationType.PAYMENT_UPDATE_REQUESTED, payment.getId(), tenantUserId);
            publishTenantUpdateAction(spaceId, tenantUserId, actorId, payment, message, tenantKey);
        }
    }

    @Transactional
    public void onRejected(SpacePaymentEntity payment, UUID actorId, String reason) {
        UUID spaceId = payment.getSpace().getId();
        notificationService.resolveOpenForEntity(
                spaceId, NotificationEntityType.PAYMENT, payment.getId(), NotificationType.PAYMENT_NEEDS_REVIEW);
        notificationService.resolveOpenForEntity(
                spaceId, NotificationEntityType.PAYMENT, payment.getId(), NotificationType.PAYMENT_NEEDS_UPDATE);
        UUID tenantUserId = linkedUserId(payment);
        if (tenantUserId != null) {
            notificationService.resolveOpenForEntity(
                    spaceId,
                    NotificationEntityType.PAYMENT,
                    payment.getId(),
                    NotificationType.PAYMENT_UPDATE_REQUESTED);
            notificationService.publish(PublishNotificationCommand.builder()
                    .spaceId(spaceId)
                    .userId(tenantUserId)
                    .actorId(actorId)
                    .entityType(NotificationEntityType.PAYMENT)
                    .entityId(payment.getId())
                    .notificationType(NotificationType.PAYMENT_REJECTED)
                    .category(NotificationCategory.ERROR)
                    .priority(NotificationPriority.HIGH)
                    .title("Payment rejected")
                    .message(reason != null ? reason : payment.getTitle() + " was rejected")
                    .actionLabel("Update Payment")
                    .actionRoute("PaymentDetail")
                    .dedupeKey("INFO:" + NotificationType.PAYMENT_REJECTED + ":" + payment.getId() + ":"
                            + tenantUserId)
                    .build());
        }
    }

    private void publishTenantUpdateAction(
            UUID spaceId,
            UUID tenantUserId,
            UUID actorId,
            SpacePaymentEntity payment,
            String message,
            String dedupeKey) {
        notificationService.publish(PublishNotificationCommand.builder()
                .spaceId(spaceId)
                .userId(tenantUserId)
                .actorId(actorId)
                .entityType(NotificationEntityType.PAYMENT)
                .entityId(payment.getId())
                .notificationType(NotificationType.PAYMENT_UPDATE_REQUESTED)
                .category(NotificationCategory.ACTION_REQUIRED)
                .priority(NotificationPriority.HIGH)
                .title("Payment requires update")
                .message(message != null && !message.isBlank()
                        ? message
                        : "Owner requested changes to your payment")
                .actionLabel("Update Payment")
                .actionRoute("PaymentDetail")
                .dedupeKey(dedupeKey)
                .build());
    }

    private void resolveStaleTenantUpdateActions(
            UUID spaceId, List<SpacePaymentEntity> payments, Set<String> expectedTenantKeys) {
        Set<UUID> tenantIds = new HashSet<>();
        for (SpacePaymentEntity payment : payments) {
            UUID tenantUserId = linkedUserId(payment);
            if (tenantUserId != null) {
                tenantIds.add(tenantUserId);
            }
        }
        for (UUID tenantId : tenantIds) {
            for (SpaceNotificationEntity open : notificationService.listOpenActions(spaceId, tenantId)) {
                if (open.getNotificationType() != NotificationType.PAYMENT_UPDATE_REQUESTED) {
                    continue;
                }
                if (!expectedTenantKeys.contains(open.getDedupeKey())) {
                    notificationService.resolveOpenForEntity(
                            spaceId,
                            NotificationEntityType.PAYMENT,
                            open.getEntityId(),
                            NotificationType.PAYMENT_UPDATE_REQUESTED);
                }
            }
        }
    }

    private void resolveStaleOwnerPaymentActions(
            UUID spaceId, List<UUID> managerIds, Set<String> expectedDedupeKeys) {
        for (UUID managerId : managerIds) {
            for (SpaceNotificationEntity open : notificationService.listOpenActions(spaceId, managerId)) {
                if (!OWNER_PAYMENT_ACTION_TYPES.contains(open.getNotificationType())) {
                    continue;
                }
                if (!expectedDedupeKeys.contains(open.getDedupeKey())) {
                    notificationService.resolveOpenForEntity(
                            spaceId,
                            NotificationEntityType.PAYMENT,
                            open.getEntityId(),
                            open.getNotificationType());
                }
            }
        }
    }

    private void publishOwnerAction(
            UUID spaceId,
            UUID managerId,
            SpacePaymentEntity payment,
            NotificationType type,
            String title,
            String message,
            String actionLabel,
            String actionRoute,
            NotificationPriority priority,
            String dedupeKey) {
        notificationService.publish(PublishNotificationCommand.builder()
                .spaceId(spaceId)
                .userId(managerId)
                .entityType(NotificationEntityType.PAYMENT)
                .entityId(payment.getId())
                .notificationType(type)
                .category(NotificationCategory.ACTION_REQUIRED)
                .priority(priority)
                .title(title)
                .message(message)
                .actionLabel(actionLabel)
                .actionRoute(actionRoute)
                .dedupeKey(dedupeKey)
                .build());
    }

    private List<UUID> managerUserIds(UUID spaceId) {
        return membershipRepository.findBySpaceIdAndStatus(spaceId, MembershipStatus.ACTIVE).stream()
                .filter(m -> m.getRole() == MembershipRole.OWNER || m.getRole() == MembershipRole.MANAGER)
                .map(m -> m.getUser().getId())
                .distinct()
                .toList();
    }

    private static String memberLabel(SpacePaymentEntity payment) {
        return payment.getMember() != null && payment.getMember().getFullName() != null
                ? payment.getMember().getFullName()
                : "Member";
    }

    private static UUID linkedUserId(SpacePaymentEntity payment) {
        if (payment.getMember() == null || payment.getMember().getUser() == null) {
            return null;
        }
        return payment.getMember().getUser().getId();
    }

    public static String actionDedupeKey(NotificationType type, UUID entityId, UUID userId) {
        return type.name() + ":" + entityId + ":" + userId;
    }
}
