package com.acomi.acomi_backend.member.application.support;

import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentCategory;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentType;
import java.math.BigDecimal;
import java.util.List;

/**
 * Resolves the member deposit summary from the stay contract and the payment ledger.
 *
 * <p>The active stay's deposit snapshot is the contract amount. Transfer history is already
 * applied there: KEEP copies the previous deposit, APPLY_NEW and CUSTOM store the new amount.
 * Vacated stays are not added on top of the active contract.
 *
 * <p>Amount paid counts only security-deposit payments whose status is PAID. A pending, under
 * review, or rejected row does not count, even if the member record still has a manual paid
 * figure. When no security-deposit payment exists, the manual member figure is kept for older
 * records that never created a payment.
 *
 * <p>Refunds prefer paid REFUND payments. The manual refund figure is used only when no paid
 * refund payment exists, so the same refund is not counted twice.
 *
 * <p>Balance follows the existing rule: deposit paid minus deposit refunded.
 */
public final class MemberDepositSummaryCalculator {

    private MemberDepositSummaryCalculator() {}

    public record StayDeposit(OccupancyStatus status, BigDecimal depositSnapshot) {}

    public record DepositPayment(
            SpacePaymentType type,
            SpacePaymentCategory category,
            SpacePaymentStatus status,
            BigDecimal amount) {}

    public static MemberDepositSummary summarize(
            List<StayDeposit> stays,
            List<DepositPayment> payments,
            BigDecimal manualAmount,
            BigDecimal manualPaid,
            BigDecimal manualRefunded) {
        BigDecimal amount = resolveAmount(stays, manualAmount);
        BigDecimal paid = resolvePaid(payments, manualPaid);
        BigDecimal refunded = resolveRefunded(payments, manualRefunded);
        return new MemberDepositSummary(amount, paid, refunded, paid.subtract(refunded));
    }

    public static boolean hasSecurityDepositPayment(List<DepositPayment> payments) {
        if (payments == null) {
            return false;
        }
        return payments.stream().anyMatch(MemberDepositSummaryCalculator::isSecurityDeposit);
    }

    static BigDecimal resolveAmount(List<StayDeposit> stays, BigDecimal manualAmount) {
        StayDeposit active = firstStay(stays, OccupancyStatus.ACTIVE);
        if (active != null) {
            return money(active.depositSnapshot());
        }
        StayDeposit reserved = firstStay(stays, OccupancyStatus.RESERVED);
        if (reserved != null) {
            return money(reserved.depositSnapshot());
        }
        return money(manualAmount);
    }

    public static BigDecimal resolvePaid(List<DepositPayment> payments, BigDecimal manualPaid) {
        if (!hasSecurityDepositPayment(payments)) {
            return money(manualPaid);
        }
        BigDecimal received = BigDecimal.ZERO;
        for (DepositPayment payment : payments) {
            if (isSecurityDeposit(payment) && payment.status() == SpacePaymentStatus.PAID) {
                received = received.add(money(payment.amount()));
            }
        }
        return received;
    }

    static BigDecimal resolveRefunded(List<DepositPayment> payments, BigDecimal manualRefunded) {
        BigDecimal recorded = BigDecimal.ZERO;
        boolean sawPaidRefund = false;
        if (payments != null) {
            for (DepositPayment payment : payments) {
                if (payment.category() == SpacePaymentCategory.REFUND
                        && payment.status() == SpacePaymentStatus.PAID) {
                    sawPaidRefund = true;
                    recorded = recorded.add(money(payment.amount()));
                }
            }
        }
        if (sawPaidRefund) {
            return recorded;
        }
        return money(manualRefunded);
    }

    private static boolean isSecurityDeposit(DepositPayment payment) {
        return payment != null
                && payment.type() == SpacePaymentType.DEPOSIT
                && payment.category() == SpacePaymentCategory.SECURITY;
    }

    private static StayDeposit firstStay(List<StayDeposit> stays, OccupancyStatus status) {
        if (stays == null) {
            return null;
        }
        for (StayDeposit stay : stays) {
            if (stay != null && stay.status() == status) {
                return stay;
            }
        }
        return null;
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
