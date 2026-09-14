package com.acomi.acomi_backend.mail.infrastructure.persistence.entity;

import com.acomi.acomi_backend.common.model.BaseEntity;
import com.acomi.acomi_backend.mail.domain.model.EmailEventType;
import com.acomi.acomi_backend.mail.domain.model.EmailSendStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "email_send_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailSendLogEntity extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private EmailEventType eventType;

    @Column(name = "recipient_user_id")
    private UUID recipientUserId;

    @Column(name = "recipient_email", nullable = false, length = 255)
    private String recipientEmail;

    @Column(name = "from_email", nullable = false, length = 255)
    private String fromEmail;

    @Column(name = "reply_to", length = 255)
    private String replyTo;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Column(name = "related_entity_type", length = 40)
    private String relatedEntityType;

    @Column(name = "related_entity_id")
    private UUID relatedEntityId;

    @Column(name = "idempotency_key", nullable = false, length = 120, unique = true)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EmailSendStatus status;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    @Column(name = "provider_message_id", length = 200)
    private String providerMessageId;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;
}
