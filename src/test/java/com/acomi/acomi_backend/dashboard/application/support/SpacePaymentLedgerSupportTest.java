package com.acomi.acomi_backend.dashboard.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.dashboard.domain.model.MemberPaymentStatus;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentCategory;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentType;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.SpacePaymentRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpacePaymentLedgerSupportTest {

    @Mock
    private SpacePaymentRepository paymentRepository;

    private SpacePaymentLedgerSupport support;

    @BeforeEach
    void setUp() {
        support = new SpacePaymentLedgerSupport(paymentRepository);
    }

    @Test
    void resolvesPaidCollectedAmount() {
        SpacePaymentEntity paid = payment(SpacePaymentStatus.PAID, "10000");
        assertThat(support.sumPaidAmount(List.of(paid))).isEqualByComparingTo("10000");
    }

    @Test
    void sumsUnderReviewSeparatelyFromPaid() {
        List<SpacePaymentEntity> payments = List.of(
                payment(SpacePaymentStatus.UNDER_REVIEW, "180"),
                payment(SpacePaymentStatus.PENDING, "570"),
                payment(SpacePaymentStatus.PAID, "0"));
        assertThat(support.sumUnderReviewAmount(payments)).isEqualByComparingTo("180");
        assertThat(support.computePendingAmount(new BigDecimal("750"), BigDecimal.ZERO, new BigDecimal("180")))
                .isEqualByComparingTo("570");
    }

    @Test
    void marksUnderReviewWhenAllOutstandingIsSubmitted() {
        SpacePaymentEntity underReview = payment(SpacePaymentStatus.UNDER_REVIEW, "180");
        SpacePaymentEntity paid = payment(SpacePaymentStatus.PAID, "570");
        assertThat(support.resolveRowStatus(
                        new BigDecimal("750"),
                        new BigDecimal("570"),
                        new BigDecimal("180"),
                        List.of(underReview, paid)))
                .isEqualTo(MemberPaymentStatus.UNDER_REVIEW);
    }

    @Test
    void marksUnderReviewWhenResidualCoveredEvenIfStalePendingRowsRemain() {
        // Mess day proofs under review covering full expected, while old PENDING expected rows linger.
        SpacePaymentEntity dayA = payment(SpacePaymentStatus.UNDER_REVIEW, "450");
        SpacePaymentEntity dayB = payment(SpacePaymentStatus.UNDER_REVIEW, "640");
        SpacePaymentEntity dayC = payment(SpacePaymentStatus.UNDER_REVIEW, "680");
        SpacePaymentEntity stalePending = payment(SpacePaymentStatus.PENDING, "1770");
        assertThat(support.resolveRowStatus(
                        new BigDecimal("1770"),
                        BigDecimal.ZERO,
                        new BigDecimal("1770"),
                        List.of(dayA, dayB, dayC, stalePending)))
                .isEqualTo(MemberPaymentStatus.UNDER_REVIEW);
    }

    @Test
    void prefersPendingWhenAnyCustomerActionRemains() {
        SpacePaymentEntity underReview = payment(SpacePaymentStatus.UNDER_REVIEW, "180");
        SpacePaymentEntity pending = payment(SpacePaymentStatus.PENDING, "150");
        SpacePaymentEntity paid = payment(SpacePaymentStatus.PAID, "420");
        assertThat(support.resolveRowStatus(
                        new BigDecimal("750"),
                        new BigDecimal("420"),
                        new BigDecimal("180"),
                        List.of(underReview, pending, paid)))
                .isEqualTo(MemberPaymentStatus.PENDING);
    }

    @Test
    void marksPartialWhenSomeCollectedAndRemainderPendingWithoutReview() {
        SpacePaymentEntity paid = payment(SpacePaymentStatus.PAID, "4000");
        SpacePaymentEntity pending = payment(SpacePaymentStatus.PENDING, "6000");
        assertThat(support.resolveRowStatus(
                        new BigDecimal("10000"),
                        new BigDecimal("4000"),
                        BigDecimal.ZERO,
                        List.of(paid, pending)))
                .isEqualTo(MemberPaymentStatus.PARTIAL);
    }

    @Test
    void marksPaidWhenRentApproved() {
        SpacePaymentEntity paid = payment(SpacePaymentStatus.PAID, "10000");
        assertThat(support.resolveRowStatus(
                        new BigDecimal("10000"),
                        new BigDecimal("10000"),
                        BigDecimal.ZERO,
                        List.of(paid)))
                .isEqualTo(MemberPaymentStatus.PAID);
    }

    @Test
    void exposesChangesRequestedQueueStatus() {
        SpacePaymentEntity updateRequested = payment(SpacePaymentStatus.UPDATE_REQUESTED, "10000");
        assertThat(support.resolveRowStatus(
                        new BigDecimal("10000"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        List.of(updateRequested)))
                .isEqualTo(MemberPaymentStatus.UPDATE_REQUESTED);
    }

    @Test
    void sumsDepositAndManualObligationsSeparatelyFromRent() {
        SpacePaymentEntity rent = payment(SpacePaymentStatus.PENDING, "3400");
        SpacePaymentEntity deposit = SpacePaymentEntity.builder()
                .paymentType(SpacePaymentType.DEPOSIT)
                .paymentCategory(SpacePaymentCategory.SECURITY)
                .paymentStatus(SpacePaymentStatus.PENDING)
                .amount(new BigDecimal("3000"))
                .build();
        SpacePaymentEntity electricity = SpacePaymentEntity.builder()
                .paymentType(SpacePaymentType.OTHER)
                .paymentCategory(SpacePaymentCategory.ELECTRICITY)
                .paymentStatus(SpacePaymentStatus.PENDING)
                .amount(new BigDecimal("850"))
                .build();
        SpacePaymentEntity mealMonthly = SpacePaymentEntity.builder()
                .paymentType(SpacePaymentType.MEAL)
                .paymentCategory(SpacePaymentCategory.MONTHLY)
                .paymentStatus(SpacePaymentStatus.PENDING)
                .amount(new BigDecimal("1200"))
                .build();
        SpacePaymentEntity mealExtra = SpacePaymentEntity.builder()
                .paymentType(SpacePaymentType.MEAL)
                .paymentCategory(SpacePaymentCategory.EXTRA)
                .paymentStatus(SpacePaymentStatus.PENDING)
                .amount(new BigDecimal("400"))
                .build();
        SpacePaymentEntity penalty = SpacePaymentEntity.builder()
                .paymentType(SpacePaymentType.PENALTY)
                .paymentCategory(SpacePaymentCategory.EXTRA)
                .paymentStatus(SpacePaymentStatus.PENDING)
                .amount(new BigDecimal("200"))
                .build();
        SpacePaymentEntity advanceRent = SpacePaymentEntity.builder()
                .paymentType(SpacePaymentType.RENT)
                .paymentCategory(SpacePaymentCategory.ADVANCE)
                .paymentStatus(SpacePaymentStatus.PAID)
                .amount(new BigDecimal("6000"))
                .build();
        assertThat(support.sumNonRecurringObligationAmount(
                        List.of(rent, deposit, electricity, mealMonthly, mealExtra, penalty, advanceRent)))
                .isEqualByComparingTo("10450");
        assertThat(support.sumRecurringRentAmount(
                        List.of(rent, deposit, electricity, advanceRent)))
                .isEqualByComparingTo("3400");
        assertThat(support.sumRentAdvanceAmount(List.of(rent, deposit, advanceRent)))
                .isEqualByComparingTo("6000");
    }

    @Test
    void netCollectedSubtractsRefundRowsAndIgnoresRefundAsIncome() {
        SpacePaymentEntity collected = SpacePaymentEntity.builder()
                .paymentType(SpacePaymentType.RENT)
                .paymentCategory(SpacePaymentCategory.ADVANCE)
                .paymentStatus(SpacePaymentStatus.PAID)
                .amount(new BigDecimal("5000"))
                .build();
        SpacePaymentEntity refund = SpacePaymentEntity.builder()
                .paymentType(SpacePaymentType.RENT)
                .paymentCategory(SpacePaymentCategory.REFUND)
                .paymentStatus(SpacePaymentStatus.PAID)
                .amount(new BigDecimal("2000"))
                .build();
        assertThat(support.sumPaidAmount(List.of(collected, refund))).isEqualByComparingTo("3000");
    }

    @Test
    void fullyRefundedObligationNetsToZero() {
        SpacePaymentEntity original = SpacePaymentEntity.builder()
                .paymentType(SpacePaymentType.RENT)
                .paymentCategory(SpacePaymentCategory.ADVANCE)
                .paymentStatus(SpacePaymentStatus.REFUNDED)
                .amount(new BigDecimal("5000"))
                .build();
        SpacePaymentEntity refund = SpacePaymentEntity.builder()
                .paymentType(SpacePaymentType.RENT)
                .paymentCategory(SpacePaymentCategory.REFUND)
                .paymentStatus(SpacePaymentStatus.PAID)
                .amount(new BigDecimal("5000"))
                .build();
        assertThat(support.sumPaidAmount(List.of(original, refund))).isEqualByComparingTo("0");
    }

    @Test
    void fullRefundDoesNotCreatePending() {
        SpacePaymentEntity original = SpacePaymentEntity.builder()
                .paymentType(SpacePaymentType.RENT)
                .paymentCategory(SpacePaymentCategory.MONTHLY)
                .paymentStatus(SpacePaymentStatus.REFUNDED)
                .amount(new BigDecimal("7000"))
                .build();
        SpacePaymentEntity refund = SpacePaymentEntity.builder()
                .paymentType(SpacePaymentType.RENT)
                .paymentCategory(SpacePaymentCategory.REFUND)
                .paymentStatus(SpacePaymentStatus.PAID)
                .amount(new BigDecimal("7000"))
                .build();
        List<SpacePaymentEntity> payments = List.of(original, refund);
        assertThat(support.sumPaidAmount(payments)).isEqualByComparingTo("0");
        assertThat(support.sumUnpaidObligationAmount(payments)).isEqualByComparingTo("0");
        assertThat(support.computePendingAmount(
                        new BigDecimal("7000"), BigDecimal.ZERO, BigDecimal.ZERO, payments))
                .isEqualByComparingTo("0");
    }

    @Test
    void cancelledMonthlyRentIsNotAnUnpaidObligation() {
        SpacePaymentEntity cancelled = payment(SpacePaymentStatus.CANCELLED, "5000");
        assertThat(support.sumUnpaidObligationAmount(List.of(cancelled))).isEqualByComparingTo("0");
        assertThat(support.computePendingAmount(
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of(cancelled)))
                .isEqualByComparingTo("0");
    }

    @Test
    void openReissueIsNotStillTreatedAsCancelledRent() {
        SpacePaymentEntity cancelled = payment(SpacePaymentStatus.CANCELLED, "6000");
        SpacePaymentEntity reissue = SpacePaymentEntity.builder()
                .paymentType(SpacePaymentType.RENT)
                .paymentCategory(SpacePaymentCategory.MONTHLY)
                .paymentStatus(SpacePaymentStatus.PENDING)
                .amount(new BigDecimal("6000"))
                .relatedPayment(cancelled)
                .build();
        assertThat(support.sumCancelledRecurringRentAmount(List.of(cancelled, reissue)))
                .isEqualByComparingTo("6000");
        assertThat(support.sumOpenReissuedMonthlyRent(List.of(cancelled, reissue)))
                .isEqualByComparingTo("6000");
        assertThat(support.sumUnpaidObligationAmount(List.of(cancelled, reissue)))
                .isEqualByComparingTo("6000");
    }

    @Test
    void refundDoesNotReopenPendingWhenNoUnpaidObligationsRemain() {
        SpacePaymentEntity original = SpacePaymentEntity.builder()
                .paymentType(SpacePaymentType.RENT)
                .paymentCategory(SpacePaymentCategory.MONTHLY)
                .paymentStatus(SpacePaymentStatus.PAID)
                .amount(new BigDecimal("5500"))
                .refundedAmount(new BigDecimal("1200"))
                .retainedAmount(new BigDecimal("4300"))
                .build();
        SpacePaymentEntity refund = SpacePaymentEntity.builder()
                .paymentType(SpacePaymentType.RENT)
                .paymentCategory(SpacePaymentCategory.REFUND)
                .paymentStatus(SpacePaymentStatus.PAID)
                .amount(new BigDecimal("1200"))
                .build();
        List<SpacePaymentEntity> payments = List.of(original, refund);
        assertThat(support.sumPaidAmount(payments)).isEqualByComparingTo("4300");
        assertThat(support.computePendingAmount(
                        new BigDecimal("5500"), new BigDecimal("4300"), BigDecimal.ZERO, payments))
                .isEqualByComparingTo("0");
        assertThat(support.resolveRowStatus(
                        new BigDecimal("5500"), new BigDecimal("4300"), BigDecimal.ZERO, payments))
                .isEqualTo(MemberPaymentStatus.PAID);
    }

    @Test
    void unpaidPendingUsesObligationRowsNotResidualAfterPartialCollect() {
        SpacePaymentEntity paid = payment(SpacePaymentStatus.PAID, "4000");
        SpacePaymentEntity pending = payment(SpacePaymentStatus.PENDING, "6000");
        List<SpacePaymentEntity> payments = List.of(paid, pending);
        assertThat(support.sumUnpaidObligationAmount(payments)).isEqualByComparingTo("6000");
        assertThat(support.computePendingAmount(
                        new BigDecimal("10000"), new BigDecimal("4000"), BigDecimal.ZERO, payments))
                .isEqualByComparingTo("6000");
    }

    @Test
    void cancelledRentIsSummedSeparatelyForExpectedAdjustment() {
        SpacePaymentEntity cancelled = payment(SpacePaymentStatus.CANCELLED, "5500");
        SpacePaymentEntity open = payment(SpacePaymentStatus.PENDING, "5500");
        assertThat(support.sumCancelledRecurringRentAmount(List.of(cancelled, open)))
                .isEqualByComparingTo("5500");
    }

    @Test
    void cancelledObligationsAreExcludedFromExpectedExtrasAndRent() {
        SpacePaymentEntity cancelledRent = payment(SpacePaymentStatus.CANCELLED, "4000");
        SpacePaymentEntity cancelledDeposit = SpacePaymentEntity.builder()
                .paymentType(SpacePaymentType.DEPOSIT)
                .paymentCategory(SpacePaymentCategory.SECURITY)
                .paymentStatus(SpacePaymentStatus.CANCELLED)
                .amount(new BigDecimal("3000"))
                .build();
        SpacePaymentEntity openRent = payment(SpacePaymentStatus.PENDING, "4000");
        assertThat(support.sumRecurringRentAmount(List.of(cancelledRent, openRent)))
                .isEqualByComparingTo("4000");
        assertThat(support.sumNonRecurringObligationAmount(List.of(cancelledDeposit)))
                .isEqualByComparingTo("0");
    }

    @Test
    void bookingAdvanceIsIdentifiableForOccupancyBillingDeduction() {
        SpacePaymentEntity remaining = SpacePaymentEntity.builder()
                .paymentType(SpacePaymentType.RENT)
                .paymentCategory(SpacePaymentCategory.MONTHLY)
                .paymentStatus(SpacePaymentStatus.PENDING)
                .amount(new BigDecimal("4000"))
                .build();
        SpacePaymentEntity advance = SpacePaymentEntity.builder()
                .paymentType(SpacePaymentType.RENT)
                .paymentCategory(SpacePaymentCategory.ADVANCE)
                .paymentStatus(SpacePaymentStatus.PAID)
                .amount(new BigDecimal("2000"))
                .build();
        assertThat(support.sumRentAdvanceAmount(List.of(remaining, advance)))
                .isEqualByComparingTo("2000");
        assertThat(support.sumRecurringRentAmount(List.of(remaining, advance)))
                .isEqualByComparingTo("4000");
        // Fallback path (no occupancy billing): MONTHLY + ADVANCE extras = full ₹6,000.
        assertThat(support
                        .sumRecurringRentAmount(List.of(remaining, advance))
                        .add(support.sumNonRecurringObligationAmount(List.of(remaining, advance))))
                .isEqualByComparingTo("6000");
    }

    @Test
    void cancelledBookingAdvanceDepositAndRemainingDoNotAffectPendingOrExtras() {
        // Sunrise PG 77 / Member Four cancelled reservation obligations.
        SpacePaymentEntity advance = SpacePaymentEntity.builder()
                .paymentType(SpacePaymentType.RENT)
                .paymentCategory(SpacePaymentCategory.ADVANCE)
                .paymentStatus(SpacePaymentStatus.CANCELLED)
                .amount(new BigDecimal("2000"))
                .build();
        SpacePaymentEntity remaining = SpacePaymentEntity.builder()
                .paymentType(SpacePaymentType.RENT)
                .paymentCategory(SpacePaymentCategory.MONTHLY)
                .paymentStatus(SpacePaymentStatus.CANCELLED)
                .amount(new BigDecimal("4000"))
                .build();
        SpacePaymentEntity deposit = SpacePaymentEntity.builder()
                .paymentType(SpacePaymentType.DEPOSIT)
                .paymentCategory(SpacePaymentCategory.SECURITY)
                .paymentStatus(SpacePaymentStatus.CANCELLED)
                .amount(new BigDecimal("3000"))
                .build();
        List<SpacePaymentEntity> cancelled = List.of(advance, remaining, deposit);

        assertThat(support.sumUnpaidObligationAmount(cancelled)).isEqualByComparingTo("0");
        assertThat(support.sumNonRecurringObligationAmount(cancelled)).isEqualByComparingTo("0");
        assertThat(support.sumRecurringRentAmount(cancelled)).isEqualByComparingTo("0");
        assertThat(support.sumRentAdvanceAmount(cancelled)).isEqualByComparingTo("0");
        assertThat(support.sumCancelledRecurringRentAmount(cancelled)).isEqualByComparingTo("4000");
        assertThat(support.computePendingAmount(
                        new BigDecimal("5600"), BigDecimal.ZERO, BigDecimal.ZERO, cancelled))
                .isEqualByComparingTo("0");
    }

    @Test
    void sunrisePg77VisibleOverdueObligationsReconcilePendingAt19666_67() {
        SpacePaymentEntity memberOneRent = payment(SpacePaymentStatus.PENDING, "6666.67");
        SpacePaymentEntity memberOneDeposit = SpacePaymentEntity.builder()
                .paymentType(SpacePaymentType.DEPOSIT)
                .paymentCategory(SpacePaymentCategory.SECURITY)
                .paymentStatus(SpacePaymentStatus.PENDING)
                .amount(new BigDecimal("5000"))
                .build();
        SpacePaymentEntity memberTwoRent = payment(SpacePaymentStatus.PENDING, "5000");
        SpacePaymentEntity memberThreeDeposit = SpacePaymentEntity.builder()
                .paymentType(SpacePaymentType.DEPOSIT)
                .paymentCategory(SpacePaymentCategory.SECURITY)
                .paymentStatus(SpacePaymentStatus.PENDING)
                .amount(new BigDecimal("3000"))
                .build();
        List<SpacePaymentEntity> unpaid = List.of(
                memberOneRent, memberOneDeposit, memberTwoRent, memberThreeDeposit);

        assertThat(support.sumUnpaidObligationAmount(unpaid)).isEqualByComparingTo("19666.67");
        // When Expected equals unpaid SoT, Pending matches Expected (no open-ended ghost accrual).
        assertThat(support.computePendingAmount(
                        new BigDecimal("19666.67"), BigDecimal.ZERO, BigDecimal.ZERO, unpaid))
                .isEqualByComparingTo("19666.67");
        // Inflated Expected from open-ended accrual must not raise Pending above unpaid SoT.
        assertThat(support.computePendingAmount(
                        new BigDecimal("25266.67"), BigDecimal.ZERO, BigDecimal.ZERO, unpaid))
                .isEqualByComparingTo("19666.67");
    }

    private SpacePaymentEntity payment(SpacePaymentStatus status, String amount) {
        return SpacePaymentEntity.builder()
                .paymentType(SpacePaymentType.RENT)
                .paymentCategory(SpacePaymentCategory.MONTHLY)
                .paymentStatus(status)
                .amount(new BigDecimal(amount))
                .build();
    }
}
