package com.acomi.acomi_backend.payment.application.support;

import com.acomi.acomi_backend.meal.application.support.MealPollCloseAtCalculator;
import com.acomi.acomi_backend.payment.domain.model.PaymentSettlementStatus;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;

/**
 * Authoritative settlement + overdue derivation for space payments.
 * Does not mutate stored workflow {@link SpacePaymentStatus}.
 */
public final class PaymentDueStatusCalculator {

    /** Workflow statuses that still have customer liability for reminder purposes. */
    public static final Set<SpacePaymentStatus> REMINDER_ELIGIBLE_STATUSES = EnumSet.of(
            SpacePaymentStatus.PENDING,
            SpacePaymentStatus.REJECTED,
            SpacePaymentStatus.UPDATE_REQUESTED);

    private PaymentDueStatusCalculator() {}

    @Getter
    @Builder
    public static final class DueStatus {
        private final BigDecimal totalAmount;
        private final BigDecimal paidAmount;
        private final BigDecimal outstandingAmount;
        private final PaymentSettlementStatus settlementStatus;
        private final boolean overdue;
        private final int daysOverdue;
        private final boolean reminderEligible;
        private final LocalDate businessDate;
    }

    public static LocalDate businessDate(SpaceEntity space) {
        return MealPollCloseAtCalculator.nowInSpace(space).toLocalDate();
    }

    public static DueStatus calculate(SpacePaymentEntity payment, LocalDate businessDate) {
        Objects.requireNonNull(payment, "payment");
        Objects.requireNonNull(businessDate, "businessDate");

        BigDecimal total = payment.getAmount() != null ? payment.getAmount() : BigDecimal.ZERO;
        // Current ledger: PAID means fully collected; no partial paidAmount column yet.
        BigDecimal paid = payment.getPaymentStatus() == SpacePaymentStatus.PAID ? total : BigDecimal.ZERO;
        BigDecimal outstanding = total.subtract(paid).max(BigDecimal.ZERO);

        PaymentSettlementStatus settlement;
        if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
            settlement = PaymentSettlementStatus.PAID;
        } else if (paid.compareTo(BigDecimal.ZERO) > 0) {
            settlement = PaymentSettlementStatus.PARTIALLY_PAID;
        } else {
            settlement = PaymentSettlementStatus.UNPAID;
        }

        boolean overdue = payment.getDueDate() != null
                && businessDate.isAfter(payment.getDueDate())
                && outstanding.compareTo(BigDecimal.ZERO) > 0;

        int daysOverdue = 0;
        if (overdue) {
            daysOverdue = (int) ChronoUnit.DAYS.between(payment.getDueDate(), businessDate);
            if (daysOverdue < 0) {
                daysOverdue = 0;
            }
        }

        boolean reminderEligible = overdue
                && REMINDER_ELIGIBLE_STATUSES.contains(payment.getPaymentStatus())
                && payment.getMember() != null
                && payment.getMember().isActive();

        return DueStatus.builder()
                .totalAmount(total)
                .paidAmount(paid)
                .outstandingAmount(outstanding)
                .settlementStatus(settlement)
                .overdue(overdue)
                .daysOverdue(daysOverdue)
                .reminderEligible(reminderEligible)
                .businessDate(businessDate)
                .build();
    }

    public static DueStatus calculate(SpacePaymentEntity payment, SpaceEntity space) {
        return calculate(payment, businessDate(space));
    }
}
