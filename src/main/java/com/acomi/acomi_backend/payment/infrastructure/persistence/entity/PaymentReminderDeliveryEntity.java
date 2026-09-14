package com.acomi.acomi_backend.payment.infrastructure.persistence.entity;

import com.acomi.acomi_backend.common.model.BaseEntity;
import com.acomi.acomi_backend.payment.domain.model.PaymentReminderType;
import com.acomi.acomi_backend.payment.domain.model.ReminderChannel;
import com.acomi.acomi_backend.payment.domain.model.ReminderDeliveryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payment_reminder_deliveries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentReminderDeliveryEntity extends BaseEntity {

    @Column(name = "space_id", nullable = false)
    private UUID spaceId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "recipient_member_id", nullable = false)
    private UUID recipientMemberId;

    @Column(name = "recipient_user_id")
    private UUID recipientUserId;

    @Column(name = "recipient_mobile", length = 32)
    private String recipientMobile;

    @Enumerated(EnumType.STRING)
    @Column(name = "reminder_type", nullable = false, length = 40)
    private PaymentReminderType reminderType;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private ReminderChannel channel;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    private ReminderDeliveryStatus deliveryStatus;

    @Column(name = "outstanding_amount", precision = 12, scale = 2)
    private BigDecimal outstandingAmount;

    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Column(name = "days_overdue")
    private Integer daysOverdue;

    @Column(name = "provider_message_id", length = 200)
    private String providerMessageId;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 1;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;
}
