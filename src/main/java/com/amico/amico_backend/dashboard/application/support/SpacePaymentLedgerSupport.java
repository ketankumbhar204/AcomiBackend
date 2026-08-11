package com.amico.amico_backend.dashboard.application.support;

import com.amico.amico_backend.dashboard.domain.model.MemberPaymentStatus;
import com.amico.amico_backend.payment.domain.model.SpacePaymentStatus;
import com.amico.amico_backend.payment.domain.model.SpacePaymentType;
import com.amico.amico_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.amico.amico_backend.payment.infrastructure.persistence.repository.SpacePaymentRepository;
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

    public BigDecimal sumPaidAmount(List<SpacePaymentEntity> payments) {
        return sumByStatuses(payments, SpacePaymentStatus.PAID);
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
     * Customer-action residual: expected − collected − under review.
     * Rejected / needs-update amounts stay in pending until the customer resubmits.
     */
    public BigDecimal computePendingAmount(
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

        BigDecimal pendingAmount = computePendingAmount(expected, collected, underReview);
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
