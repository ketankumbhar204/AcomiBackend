package com.acomi.acomi_backend.member.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.member.application.support.MemberDepositSummaryCalculator.DepositPayment;
import com.acomi.acomi_backend.member.application.support.MemberDepositSummaryCalculator.StayDeposit;
import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentCategory;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class MemberDepositSummaryCalculatorTest {

    @Test
    void unpaidContractDeposit_usesSnapshotAndIgnoresPendingPaymentAndManualPaid() {
        MemberDepositSummary summary = MemberDepositSummaryCalculator.summarize(
                List.of(stay(OccupancyStatus.ACTIVE, "3000")),
                List.of(security(SpacePaymentStatus.PENDING, "3000")),
                new BigDecimal("0"),
                new BigDecimal("3000"),
                BigDecimal.ZERO);

        assertThat(summary.getAmount()).isEqualByComparingTo("3000");
        assertThat(summary.getPaid()).isEqualByComparingTo("0");
        assertThat(summary.getRefunded()).isEqualByComparingTo("0");
        assertThat(summary.getBalance()).isEqualByComparingTo("0");
    }

    @Test
    void partiallyPaid_sumsOnlyReceivedSecurityPayments() {
        MemberDepositSummary summary = MemberDepositSummaryCalculator.summarize(
                List.of(stay(OccupancyStatus.ACTIVE, "3000")),
                List.of(
                        security(SpacePaymentStatus.PAID, "1000"),
                        security(SpacePaymentStatus.PENDING, "2000")),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO);

        assertThat(summary.getAmount()).isEqualByComparingTo("3000");
        assertThat(summary.getPaid()).isEqualByComparingTo("1000");
        assertThat(summary.getBalance()).isEqualByComparingTo("1000");
    }

    @Test
    void fullyPaid_singleReceivedPayment() {
        MemberDepositSummary summary = MemberDepositSummaryCalculator.summarize(
                List.of(stay(OccupancyStatus.ACTIVE, "3000")),
                List.of(security(SpacePaymentStatus.PAID, "3000")),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO);

        assertThat(summary.getPaid()).isEqualByComparingTo("3000");
        assertThat(summary.getBalance()).isEqualByComparingTo("3000");
    }

    @Test
    void multipleReceivedPayments_areAddedOnceEach() {
        MemberDepositSummary summary = MemberDepositSummaryCalculator.summarize(
                List.of(stay(OccupancyStatus.ACTIVE, "3000")),
                List.of(
                        security(SpacePaymentStatus.PAID, "1500"),
                        security(SpacePaymentStatus.PAID, "1500"),
                        rent(SpacePaymentStatus.PAID, "6000")),
                BigDecimal.ZERO,
                new BigDecimal("1500"),
                BigDecimal.ZERO);

        assertThat(summary.getPaid()).isEqualByComparingTo("3000");
        assertThat(summary.getBalance()).isEqualByComparingTo("3000");
    }

    @Test
    void proofAndReviewStatuses_areNotTreatedAsReceived() {
        MemberDepositSummary summary = MemberDepositSummaryCalculator.summarize(
                List.of(stay(OccupancyStatus.ACTIVE, "3000")),
                List.of(
                        security(SpacePaymentStatus.UNDER_REVIEW, "3000"),
                        security(SpacePaymentStatus.PROOF_UPLOADED, "500"),
                        security(SpacePaymentStatus.REJECTED, "3000"),
                        security(SpacePaymentStatus.UPDATE_REQUESTED, "3000")),
                BigDecimal.ZERO,
                new BigDecimal("3000"),
                BigDecimal.ZERO);

        assertThat(summary.getPaid()).isEqualByComparingTo("0");
    }

    @Test
    void refunded_reducesHeldBalanceAndDoesNotDoubleCountManualRefund() {
        MemberDepositSummary summary = MemberDepositSummaryCalculator.summarize(
                List.of(stay(OccupancyStatus.ACTIVE, "3000")),
                List.of(
                        security(SpacePaymentStatus.PAID, "3000"),
                        refund(SpacePaymentStatus.PAID, "1000"),
                        refund(SpacePaymentStatus.PENDING, "500")),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("1000"));

        assertThat(summary.getPaid()).isEqualByComparingTo("3000");
        assertThat(summary.getRefunded()).isEqualByComparingTo("1000");
        assertThat(summary.getBalance()).isEqualByComparingTo("2000");
    }

    @Test
    void manualRefund_isUsedWhenNoPaidRefundPaymentExists() {
        MemberDepositSummary summary = MemberDepositSummaryCalculator.summarize(
                List.of(stay(OccupancyStatus.ACTIVE, "3000")),
                List.of(security(SpacePaymentStatus.PAID, "3000")),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("750"));

        assertThat(summary.getRefunded()).isEqualByComparingTo("750");
        assertThat(summary.getBalance()).isEqualByComparingTo("2250");
    }

    @Test
    void legacyManualLedger_isUsedOnlyWhenNoSecurityPaymentExists() {
        MemberDepositSummary summary = MemberDepositSummaryCalculator.summarize(
                List.of(),
                List.of(),
                new BigDecimal("5000"),
                new BigDecimal("5000"),
                new BigDecimal("500"));

        assertThat(summary.getAmount()).isEqualByComparingTo("5000");
        assertThat(summary.getPaid()).isEqualByComparingTo("5000");
        assertThat(summary.getRefunded()).isEqualByComparingTo("500");
        assertThat(summary.getBalance()).isEqualByComparingTo("4500");
    }

    @Test
    void transferHistory_usesTheActiveContractAndDoesNotAddThePreviousDeposit() {
        MemberDepositSummary kept = MemberDepositSummaryCalculator.summarize(
                List.of(
                        stay(OccupancyStatus.ACTIVE, "5000"),
                        stay(OccupancyStatus.VACATED, "5000")),
                List.of(security(SpacePaymentStatus.PAID, "5000")),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO);
        MemberDepositSummary replaced = MemberDepositSummaryCalculator.summarize(
                List.of(
                        stay(OccupancyStatus.ACTIVE, "2000"),
                        stay(OccupancyStatus.VACATED, "5000")),
                List.of(security(SpacePaymentStatus.PAID, "5000")),
                new BigDecimal("5000"),
                new BigDecimal("5000"),
                BigDecimal.ZERO);

        assertThat(kept.getAmount()).isEqualByComparingTo("5000");
        assertThat(kept.getPaid()).isEqualByComparingTo("5000");
        assertThat(replaced.getAmount()).isEqualByComparingTo("2000");
        assertThat(replaced.getPaid()).isEqualByComparingTo("5000");
    }

    @Test
    void reservedContract_isUsedWhenThereIsNoActiveStay() {
        MemberDepositSummary summary = MemberDepositSummaryCalculator.summarize(
                List.of(
                        stay(OccupancyStatus.RESERVED, "3000"),
                        stay(OccupancyStatus.VACATED, "9000")),
                List.of(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO);

        assertThat(summary.getAmount()).isEqualByComparingTo("3000");
    }

    private static StayDeposit stay(OccupancyStatus status, String amount) {
        return new StayDeposit(status, new BigDecimal(amount));
    }

    private static DepositPayment security(SpacePaymentStatus status, String amount) {
        return new DepositPayment(
                SpacePaymentType.DEPOSIT,
                SpacePaymentCategory.SECURITY,
                status,
                new BigDecimal(amount));
    }

    private static DepositPayment rent(SpacePaymentStatus status, String amount) {
        return new DepositPayment(
                SpacePaymentType.RENT,
                SpacePaymentCategory.MONTHLY,
                status,
                new BigDecimal(amount));
    }

    private static DepositPayment refund(SpacePaymentStatus status, String amount) {
        return new DepositPayment(
                SpacePaymentType.DEPOSIT,
                SpacePaymentCategory.REFUND,
                status,
                new BigDecimal(amount));
    }
}
