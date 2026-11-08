package com.acomi.acomi_backend.payment.application.service;

import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.acomi.acomi_backend.payment.application.support.PaymentDueStatusCalculator;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.SpacePaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only report of OPEN_ENDED daily-rate rent rows that were generated before the
 * billing period closed (legacy advance billing). Does not delete, reverse, suppress,
 * or rewrite any ledger rows — approval is required before any historical treatment.
 */
@Service
@RequiredArgsConstructor
public class OpenEndedPrematureRentReconciliationService {

    private final SpacePaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public List<PrematureOpenEndedRentRow> findPrematureOpenEndedRentRows() {
        List<PrematureOpenEndedRentRow> rows = new ArrayList<>();
        for (SpacePaymentEntity payment :
                paymentRepository.findOpenEndedDailyRateMonthlyRentPayments()) {
            if (!isPremature(payment)) {
                continue;
            }
            LocalDate asOf = payment.getSpace() != null
                    ? PaymentDueStatusCalculator.businessDate(payment.getSpace())
                    : LocalDate.now();
            var due = PaymentDueStatusCalculator.calculate(payment, asOf);
            OccupancyEntity occupancy = payment.getOccupancy();
            rows.add(PrematureOpenEndedRentRow.builder()
                    .paymentId(payment.getId())
                    .spaceId(payment.getSpace() != null ? payment.getSpace().getId() : null)
                    .memberId(payment.getMember() != null ? payment.getMember().getId() : null)
                    .memberName(payment.getMember() != null ? payment.getMember().getFullName() : null)
                    .occupancyId(occupancy != null ? occupancy.getId() : null)
                    .billingMonth(payment.getMonth())
                    .billingPeriodStart(payment.getBillingPeriodStart())
                    .billingPeriodEnd(payment.getBillingPeriodEnd())
                    .billableDays(payment.getBillableDays())
                    .dailyRateSnapshot(payment.getConfiguredMonthlyAmount())
                    .amount(payment.getAmount())
                    .paymentStatus(payment.getPaymentStatus() != null
                            ? payment.getPaymentStatus().name()
                            : null)
                    .paidAmount(due.getPaidAmount())
                    .outstandingAmount(due.getOutstandingAmount())
                    .createdAt(payment.getCreatedAt())
                    .remarks(payment.getRemarks())
                    .build());
        }
        return rows;
    }

    /**
     * A row is premature when it was created on or before the billing period end date,
     * excluding legitimate vacate settles (period ends on vacate day).
     */
    static boolean isPremature(SpacePaymentEntity payment) {
        if (payment.getCreatedAt() == null || payment.getBillingPeriodEnd() == null) {
            return false;
        }
        LocalDate created = payment.getCreatedAt().toLocalDate();
        LocalDate periodEnd = payment.getBillingPeriodEnd();
        // Month-close (created after period end) is legitimate.
        if (created.isAfter(periodEnd)) {
            return false;
        }
        OccupancyEntity occupancy = payment.getOccupancy();
        if (occupancy != null && occupancy.getVacatedAt() != null) {
            LocalDate vacated = occupancy.getVacatedAt().toLocalDate();
            if (vacated.equals(periodEnd)) {
                // Checkout closed the period the same day — not advance billing.
                return false;
            }
        }
        String remarks = payment.getRemarks();
        if (remarks != null && remarks.toLowerCase().contains("vacate")) {
            return false;
        }
        return true;
    }

    @Getter
    @Builder
    public static class PrematureOpenEndedRentRow {
        private final UUID paymentId;
        private final UUID spaceId;
        private final UUID memberId;
        private final String memberName;
        private final UUID occupancyId;
        private final String billingMonth;
        private final LocalDate billingPeriodStart;
        private final LocalDate billingPeriodEnd;
        private final Integer billableDays;
        private final BigDecimal dailyRateSnapshot;
        private final BigDecimal amount;
        private final String paymentStatus;
        private final BigDecimal paidAmount;
        private final BigDecimal outstandingAmount;
        private final LocalDateTime createdAt;
        private final String remarks;
    }
}
