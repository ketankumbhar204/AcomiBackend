package com.acomi.acomi_backend.dashboard.application.support;

import com.acomi.acomi_backend.dashboard.domain.model.MemberPaymentStatus;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentCategory;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentType;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.SpacePaymentRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SpacePaymentLedgerSupport {

    private final SpacePaymentRepository paymentRepository;

    public Map<UUID, List<SpacePaymentEntity>> loadPaymentsByMember(UUID spaceId, String month) {
        return paymentRepository.findBySpaceIdAndMonth(spaceId, month).stream()
                .collect(Collectors.groupingBy(payment -> payment.getMember().getId()));
    }

    /** Monthly rent charge rows. Used only when occupancy billing did not already supply rent. */
    public BigDecimal sumRecurringRentAmount(List<SpacePaymentEntity> payments) {
        if (payments == null || payments.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return payments.stream()
                .filter(payment -> payment.getPaymentType() == SpacePaymentType.RENT
                        && payment.getPaymentCategory() == SpacePaymentCategory.MONTHLY)
                .filter(payment -> payment.getPaymentStatus() != SpacePaymentStatus.CANCELLED)
                .map(SpacePaymentEntity::getAmount)
                .filter(amount -> amount != null && amount.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Reservation booking-advance rows ({@code RENT/ADVANCE}) — a portion of first-period rent,
     * not an additional charge.
     */
    public BigDecimal sumRentAdvanceAmount(List<SpacePaymentEntity> payments) {
        if (payments == null || payments.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return payments.stream()
                .filter(payment -> payment.getPaymentType() == SpacePaymentType.RENT
                        && payment.getPaymentCategory() == SpacePaymentCategory.ADVANCE)
                .filter(payment -> payment.getPaymentStatus() != SpacePaymentStatus.CANCELLED)
                .map(SpacePaymentEntity::getAmount)
                .filter(amount -> amount != null && amount.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Open monthly rent created as an explicit reissue of a closed row. This amount is
     * already the live obligation, so it must not stay subtracted from Expected.
     */
    public BigDecimal sumOpenReissuedMonthlyRent(List<SpacePaymentEntity> payments) {
        if (payments == null || payments.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return payments.stream()
                .filter(payment -> payment.getPaymentType() == SpacePaymentType.RENT
                        && payment.getPaymentCategory() == SpacePaymentCategory.MONTHLY)
                .filter(payment -> payment.getRelatedPayment() != null)
                .filter(payment -> payment.getPaymentStatus() != SpacePaymentStatus.CANCELLED
                        && payment.getPaymentStatus() != SpacePaymentStatus.REFUNDED)
                .map(SpacePaymentEntity::getAmount)
                .filter(amount -> amount != null && amount.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Cancelled recurring rent rows. Used to reduce occupancy-contract Expected when the
     * owner cancels an unpaid rent obligation while the stay remains billable.
     */
    public BigDecimal sumCancelledRecurringRentAmount(List<SpacePaymentEntity> payments) {
        if (payments == null || payments.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return payments.stream()
                .filter(payment -> payment.getPaymentType() == SpacePaymentType.RENT
                        && payment.getPaymentCategory() == SpacePaymentCategory.MONTHLY)
                .filter(payment -> payment.getPaymentStatus() == SpacePaymentStatus.CANCELLED)
                .map(SpacePaymentEntity::getAmount)
                .filter(amount -> amount != null && amount.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Customer-action unpaid obligations (Pending KPI SoT).
     * Excludes CANCELLED, collected/refunded parents, REFUND cash-outs, and under-review proofs
     * (under-review is reported separately and must not inflate Pending).
     */
    public BigDecimal sumUnpaidObligationAmount(List<SpacePaymentEntity> payments) {
        if (payments == null || payments.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return payments.stream()
                .filter(this::countsAsUnpaidObligation)
                .map(SpacePaymentEntity::getAmount)
                .filter(amount -> amount != null && amount.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean countsAsUnpaidObligation(SpacePaymentEntity payment) {
        if (payment == null || payment.getPaymentCategory() == SpacePaymentCategory.REFUND) {
            return false;
        }
        SpacePaymentStatus status = payment.getPaymentStatus();
        return status == SpacePaymentStatus.PENDING
                || status == SpacePaymentStatus.REJECTED
                || status == SpacePaymentStatus.UPDATE_REQUESTED;
    }

    /**
     * One-time / manual dues that are not already in occupancy rent or meal expected.
     * Recurring rent (RENT/MONTHLY) and generated meals (MEAL/MONTHLY, MEAL/DAILY) are excluded.
     */
    public BigDecimal sumNonRecurringObligationAmount(List<SpacePaymentEntity> payments) {
        if (payments == null || payments.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return payments.stream()
                .filter(this::countsAsExtraObligation)
                .map(SpacePaymentEntity::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean countsAsExtraObligation(SpacePaymentEntity payment) {
        SpacePaymentType type = payment.getPaymentType();
        SpacePaymentCategory category = payment.getPaymentCategory();
        if (type == null || payment.getAmount() == null) {
            return false;
        }
        if (category == SpacePaymentCategory.REFUND) {
            return false;
        }
        if (payment.getPaymentStatus() == SpacePaymentStatus.CANCELLED) {
            return false;
        }
        if (type == SpacePaymentType.RENT) {
            return category != SpacePaymentCategory.MONTHLY;
        }
        if (type == SpacePaymentType.MEAL) {
            return category != SpacePaymentCategory.MONTHLY && category != SpacePaymentCategory.DAILY;
        }
        return type == SpacePaymentType.DEPOSIT
                || type == SpacePaymentType.MAINTENANCE
                || type == SpacePaymentType.UTILITY
                || type == SpacePaymentType.SERVICE
                || type == SpacePaymentType.PENALTY
                || type == SpacePaymentType.OTHER;
    }

    /**
     * Net collected for the month: verified collections minus completed refunds.
     * REFUND-category rows are cash-out and reduce the total; they are not income.
     */
    public BigDecimal sumPaidAmount(List<SpacePaymentEntity> payments) {
        if (payments == null || payments.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal collected = payments.stream()
                .filter(payment -> payment.getPaymentCategory() != SpacePaymentCategory.REFUND)
                .filter(payment -> payment.getPaymentStatus() == SpacePaymentStatus.PAID
                        || payment.getPaymentStatus() == SpacePaymentStatus.REFUNDED)
                .map(SpacePaymentEntity::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refunded = payments.stream()
                .filter(payment -> payment.getPaymentCategory() == SpacePaymentCategory.REFUND)
                .filter(payment -> payment.getPaymentStatus() == SpacePaymentStatus.PAID
                        || payment.getPaymentStatus() == SpacePaymentStatus.REFUNDED)
                .map(SpacePaymentEntity::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return collected.subtract(refunded).max(BigDecimal.ZERO);
    }

    /** Amount submitted and waiting on owner review (not customer-pending, not collected). */
    public BigDecimal sumUnderReviewAmount(List<SpacePaymentEntity> payments) {
        return sumByStatuses(payments, SpacePaymentStatus.UNDER_REVIEW, SpacePaymentStatus.PROOF_UPLOADED);
    }

    public boolean hasMealSpacePayments(List<SpacePaymentEntity> payments) {
        if (payments == null || payments.isEmpty()) {
            return false;
        }
        return payments.stream().anyMatch(payment -> payment.getPaymentType() == SpacePaymentType.MEAL);
    }

    /**
     * Pending KPI.
     * Prefer unpaid obligation rows (payment SoT) so refunds/retains do not reopen Pending.
     * When no payment list is available, fall back to residual expected − collected − under review.
     */
    public BigDecimal computePendingAmount(
            BigDecimal expected, BigDecimal collected, BigDecimal underReview) {
        return computePendingAmount(expected, collected, underReview, null);
    }

    public BigDecimal computePendingAmount(
            BigDecimal expected,
            BigDecimal collected,
            BigDecimal underReview,
            List<SpacePaymentEntity> payments) {
        if (payments != null) {
            BigDecimal unpaid = sumUnpaidObligationAmount(payments);
            // Stale PENDING rows that are already covered by under-review proofs must not inflate
            // Pending when residual is already zero (common Mess day-bridge edge case).
            BigDecimal residual = residualPending(expected, collected, underReview);
            if (residual == null) {
                return unpaid.signum() > 0 ? unpaid : null;
            }
            if (unpaid.signum() <= 0) {
                return BigDecimal.ZERO;
            }
            return unpaid.min(residual);
        }
        return residualPending(expected, collected, underReview);
    }

    private BigDecimal residualPending(
            BigDecimal expected, BigDecimal collected, BigDecimal underReview) {
        if (expected == null) {
            return null;
        }
        BigDecimal collectedAmount = collected != null ? collected : BigDecimal.ZERO;
        BigDecimal underReviewAmount = underReview != null ? underReview : BigDecimal.ZERO;
        return expected.subtract(collectedAmount).subtract(underReviewAmount).max(BigDecimal.ZERO);
    }

    /**
     * Worst outstanding state for the member badge.
     * Priority: needs-update / rejected → monetary customer residual → under review → settled.
     * Leftover PENDING payment rows must not override UNDER_REVIEW when residual is already covered
     * (common for Mess: day proofs under review while older expected PENDING rows remain).
     */
    public MemberPaymentStatus resolveRowStatus(
            BigDecimal expected,
            BigDecimal collected,
            BigDecimal underReview,
            List<SpacePaymentEntity> payments) {
        if (payments != null && !payments.isEmpty()) {
            if (payments.stream()
                    .anyMatch(payment -> payment.getPaymentStatus() == SpacePaymentStatus.UPDATE_REQUESTED)) {
                return MemberPaymentStatus.UPDATE_REQUESTED;
            }
            if (payments.stream()
                    .anyMatch(payment -> payment.getPaymentStatus() == SpacePaymentStatus.REJECTED)) {
                return MemberPaymentStatus.REJECTED;
            }
        }

        BigDecimal pendingAmount = computePendingAmount(expected, collected, underReview, payments);
        boolean monetaryCustomerResidual =
                pendingAmount != null && pendingAmount.compareTo(BigDecimal.ZERO) > 0;

        if (monetaryCustomerResidual) {
            boolean hasCollected = collected != null && collected.compareTo(BigDecimal.ZERO) > 0;
            boolean hasUnderReview =
                    underReview != null && underReview.compareTo(BigDecimal.ZERO) > 0;
            // Classic partial: some collected, remainder still needs customer payment, nothing in review.
            if (hasCollected && !hasUnderReview) {
                return MemberPaymentStatus.PARTIAL;
            }
            return MemberPaymentStatus.PENDING;
        }

        boolean hasUnderReviewPayment = payments != null
                && payments.stream()
                        .anyMatch(payment -> payment.getPaymentStatus() == SpacePaymentStatus.UNDER_REVIEW
                                || payment.getPaymentStatus() == SpacePaymentStatus.PROOF_UPLOADED);
        if (hasUnderReviewPayment
                || (underReview != null && underReview.compareTo(BigDecimal.ZERO) > 0)) {
            return MemberPaymentStatus.UNDER_REVIEW;
        }

        // After full/partial refund + retain, unpaid rows are gone even when net collected < expected.
        // Treat as settled PAID rather than PARTIAL/PENDING from residual math.
        if (collected != null && collected.compareTo(BigDecimal.ZERO) > 0) {
            return MemberPaymentStatus.PAID;
        }

        boolean hasCustomerPendingStatus = payments != null
                && payments.stream()
                        .anyMatch(payment -> payment.getPaymentStatus() == SpacePaymentStatus.PENDING);
        if (hasCustomerPendingStatus) {
            return MemberPaymentStatus.PENDING;
        }

        return deriveFinancialStatus(expected, collected);
    }

    /**
     * Prefer stored needs-update / rejected, then recompute from amounts (empty payment list).
     * Fixes stale snapshot badges where under-review money was computed but status stayed PENDING.
     */
    public MemberPaymentStatus resolveStoredRowStatus(
            BigDecimal expected,
            BigDecimal collected,
            BigDecimal underReview,
            MemberPaymentStatus stored) {
        if (stored == MemberPaymentStatus.UPDATE_REQUESTED
                || stored == MemberPaymentStatus.REJECTED) {
            return stored;
        }
        return resolveRowStatus(expected, collected, underReview, List.of());
    }

    private MemberPaymentStatus deriveFinancialStatus(BigDecimal expected, BigDecimal collected) {
        if (expected == null || expected.compareTo(BigDecimal.ZERO) <= 0) {
            return collected != null && collected.compareTo(BigDecimal.ZERO) > 0
                    ? MemberPaymentStatus.PAID
                    : MemberPaymentStatus.NONE;
        }
        if (collected == null || collected.compareTo(BigDecimal.ZERO) <= 0) {
            return MemberPaymentStatus.PENDING;
        }
        if (collected.compareTo(expected) >= 0) {
            return MemberPaymentStatus.PAID;
        }
        return MemberPaymentStatus.PARTIAL;
    }

    private BigDecimal sumByStatuses(List<SpacePaymentEntity> payments, SpacePaymentStatus... statuses) {
        if (payments == null || payments.isEmpty() || statuses.length == 0) {
            return BigDecimal.ZERO;
        }
        java.util.Set<SpacePaymentStatus> wanted = java.util.EnumSet.noneOf(SpacePaymentStatus.class);
        for (SpacePaymentStatus status : statuses) {
            wanted.add(status);
        }
        return payments.stream()
                .filter(payment -> wanted.contains(payment.getPaymentStatus()))
                .map(SpacePaymentEntity::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
