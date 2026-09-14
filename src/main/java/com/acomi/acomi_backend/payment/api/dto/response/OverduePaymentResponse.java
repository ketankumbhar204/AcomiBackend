package com.acomi.acomi_backend.payment.api.dto.response;

import com.acomi.acomi_backend.payment.domain.model.PaymentSettlementStatus;
import com.acomi.acomi_backend.payment.domain.model.ReminderDeliveryStatus;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentCategory;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/** Reminder-ready overdue payment row (no delivery channels). */
@Getter
@Builder
public class OverduePaymentResponse {

    private UUID paymentId;
    private UUID spaceId;
    private String spaceName;
    private UUID memberId;
    private String memberName;
    private String memberMobile;
    private SpacePaymentType paymentType;
    private SpacePaymentCategory paymentCategory;
    private String title;
    private String month;
    private LocalDate billingPeriodStart;
    private LocalDate billingPeriodEnd;
    private LocalDate dueDate;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal outstandingAmount;
    private String currencyCode;
    private SpacePaymentStatus paymentStatus;
    private PaymentSettlementStatus settlementStatus;
    private boolean overdue;
    private int daysOverdue;
    private boolean reminderEligible;
    /** Today's WhatsApp delivery status when a claim exists for space business date. */
    private ReminderDeliveryStatus reminderDeliveryStatusToday;
    private LocalDate reminderBusinessDate;
    private String reminderFailureReason;
}
