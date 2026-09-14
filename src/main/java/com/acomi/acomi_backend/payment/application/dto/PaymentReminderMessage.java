package com.acomi.acomi_backend.payment.application.dto;

import com.acomi.acomi_backend.payment.domain.model.PaymentReminderType;
import com.acomi.acomi_backend.payment.domain.model.ReminderChannel;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/** Channel-independent payload for overdue payment reminders. */
@Getter
@Builder
public class PaymentReminderMessage {

    private UUID paymentId;
    private UUID spaceId;
    private String spaceName;
    private UUID memberId;
    private UUID recipientUserId;
    private String memberName;
    private String recipientMobile;
    private SpacePaymentType paymentType;
    private PaymentReminderType reminderType;
    private ReminderChannel channel;
    private String title;
    private String month;
    private LocalDate billingPeriodStart;
    private LocalDate billingPeriodEnd;
    private LocalDate dueDate;
    private LocalDate businessDate;
    private int daysOverdue;
    private BigDecimal outstandingAmount;
    private String currencyCode;
}
