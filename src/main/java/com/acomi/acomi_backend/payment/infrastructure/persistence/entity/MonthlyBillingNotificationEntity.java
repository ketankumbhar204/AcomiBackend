package com.acomi.acomi_backend.payment.infrastructure.persistence.entity;

import com.acomi.acomi_backend.common.model.BaseEntity;
import com.acomi.acomi_backend.payment.domain.model.MonthlyBillingNotificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "monthly_billing_notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyBillingNotificationEntity extends BaseEntity {

    public static final String EVENT_MONTHLY_RENT_GENERATED = "MONTHLY_RENT_GENERATED";

    @Column(name = "space_id", nullable = false)
    private UUID spaceId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "recipient_member_id", nullable = false)
    private UUID recipientMemberId;

    @Column(name = "recipient_user_id")
    private UUID recipientUserId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "billing_month", nullable = false)
    private LocalDate billingMonth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private MonthlyBillingNotificationStatus status;

    @Builder.Default
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "fcm_accepted_at")
    private LocalDateTime fcmAcceptedAt;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;
}
