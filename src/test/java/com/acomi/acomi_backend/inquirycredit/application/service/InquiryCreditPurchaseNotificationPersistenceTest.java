package com.acomi.acomi_backend.inquirycredit.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.notification.application.port.in.PublishNotificationCommand;
import com.acomi.acomi_backend.notification.application.service.NotificationService;
import com.acomi.acomi_backend.notification.domain.model.NotificationCategory;
import com.acomi.acomi_backend.notification.domain.model.NotificationEntityType;
import com.acomi.acomi_backend.notification.domain.model.NotificationPriority;
import com.acomi.acomi_backend.notification.domain.model.NotificationStatus;
import com.acomi.acomi_backend.notification.domain.model.NotificationType;
import com.acomi.acomi_backend.notification.infrastructure.persistence.entity.SpaceNotificationEntity;
import com.acomi.acomi_backend.notification.infrastructure.persistence.repository.SpaceNotificationRepository;
import com.acomi.acomi_backend.user.domain.model.SystemRole;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real Postgres persistence regression for platform (non-space) inquiry-credit notifications.
 *
 * <p>Enabled only when {@code -Dacomi.local.persistence=true} so CI without the local DB is
 * unaffected. Catches {@code space_notifications.space_id NOT NULL} failures that mocks miss.
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
@EnabledIfSystemProperty(named = "acomi.local.persistence", matches = "true")
class InquiryCreditPurchaseNotificationPersistenceTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private SpaceNotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void publishInquiryCreditPending_persistsWithNullSpaceId() {
        UserEntity admin = userRepository.findBySystemRoleAndIsActiveTrue(SystemRole.ADMIN).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Local admin user required"));

        UUID purchaseRequestId = UUID.randomUUID();
        var response = notificationService.publish(PublishNotificationCommand.builder()
                .spaceId(null)
                .userId(admin.getId())
                .actorId(admin.getId())
                .entityType(NotificationEntityType.INQUIRY_CREDIT_PURCHASE)
                .entityId(purchaseRequestId)
                .notificationType(NotificationType.INQUIRY_CREDIT_PAYMENT_PENDING)
                .category(NotificationCategory.ACTION_REQUIRED)
                .priority(NotificationPriority.HIGH)
                .title("New inquiry credit purchase request")
                .message("Persistence regression")
                .actionLabel("Review request")
                .actionRoute("AdminInquiryCreditRequests")
                .dedupeKey("INQUIRY_CREDIT_PAYMENT_PENDING:" + purchaseRequestId + ":" + admin.getId())
                .build());

        assertThat(response.getSpaceId()).isNull();
        assertThat(response.getNotificationType())
                .isEqualTo(NotificationType.INQUIRY_CREDIT_PAYMENT_PENDING);

        SpaceNotificationEntity saved = notificationRepository
                .findById(response.getNotificationId())
                .orElseThrow();
        assertThat(saved.getSpaceId()).isNull();
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.UNREAD);
        assertThat(saved.getEntityType()).isEqualTo(NotificationEntityType.INQUIRY_CREDIT_PURCHASE);
        assertThat(saved.getEntityId()).isEqualTo(purchaseRequestId);
    }
}
