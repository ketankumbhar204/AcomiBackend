package com.acomi.acomi_backend.payment.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.payment.domain.model.PaymentSettlementStatus;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PaymentDueStatusCalculatorTest {

    @Test
    void dueToday_notOverdue() {
        SpacePaymentEntity payment = unpaid(LocalDate.of(2026, 9, 1), "10000");
        var due = PaymentDueStatusCalculator.calculate(payment, LocalDate.of(2026, 9, 1));

        assertThat(due.isOverdue()).isFalse();
        assertThat(due.getDaysOverdue()).isZero();
        assertThat(due.getOutstandingAmount()).isEqualByComparingTo("10000");
        assertThat(due.getSettlementStatus()).isEqualTo(PaymentSettlementStatus.UNPAID);
    }

    @Test
    void oneDayPastDue_isOverdue() {
        SpacePaymentEntity payment = unpaid(LocalDate.of(2026, 9, 1), "10000");
        var due = PaymentDueStatusCalculator.calculate(payment, LocalDate.of(2026, 9, 2));

        assertThat(due.isOverdue()).isTrue();
        assertThat(due.getDaysOverdue()).isEqualTo(1);
        assertThat(due.isReminderEligible()).isTrue();
    }

    @Test
    void multipleDaysOverdue() {
        SpacePaymentEntity payment = unpaid(LocalDate.of(2026, 9, 1), "10000");
        var due = PaymentDueStatusCalculator.calculate(payment, LocalDate.of(2026, 9, 5));

        assertThat(due.getDaysOverdue()).isEqualTo(4);
    }

    @Test
    void paid_notOverdueEvenAfterDue() {
        SpacePaymentEntity payment = unpaid(LocalDate.of(2026, 9, 1), "10000");
        payment.setPaymentStatus(SpacePaymentStatus.PAID);
        var due = PaymentDueStatusCalculator.calculate(payment, LocalDate.of(2026, 9, 5));

        assertThat(due.isOverdue()).isFalse();
        assertThat(due.getDaysOverdue()).isZero();
        assertThat(due.getOutstandingAmount()).isEqualByComparingTo("0");
        assertThat(due.getPaidAmount()).isEqualByComparingTo("10000");
        assertThat(due.getSettlementStatus()).isEqualTo(PaymentSettlementStatus.PAID);
        assertThat(due.isReminderEligible()).isFalse();
    }

    @Test
    void underReview_isOverdueButNotReminderEligible() {
        SpacePaymentEntity payment = unpaid(LocalDate.of(2026, 9, 1), "5000");
        payment.setPaymentStatus(SpacePaymentStatus.UNDER_REVIEW);
        var due = PaymentDueStatusCalculator.calculate(payment, LocalDate.of(2026, 9, 3));

        assertThat(due.isOverdue()).isTrue();
        assertThat(due.getDaysOverdue()).isEqualTo(2);
        assertThat(due.isReminderEligible()).isFalse();
    }

    @Test
    void inactiveMember_notReminderEligible() {
        SpacePaymentEntity payment = unpaid(LocalDate.of(2026, 9, 1), "5000");
        payment.getMember().setActive(false);
        var due = PaymentDueStatusCalculator.calculate(payment, LocalDate.of(2026, 9, 3));

        assertThat(due.isOverdue()).isTrue();
        assertThat(due.isReminderEligible()).isFalse();
    }

    @Test
    void februaryBoundary() {
        SpacePaymentEntity payment = unpaid(LocalDate.of(2024, 2, 29), "1000");
        var due = PaymentDueStatusCalculator.calculate(payment, LocalDate.of(2024, 3, 1));

        assertThat(due.isOverdue()).isTrue();
        assertThat(due.getDaysOverdue()).isEqualTo(1);
    }

    @Test
    void zeroOutstanding_notOverdue() {
        SpacePaymentEntity payment = unpaid(LocalDate.of(2026, 9, 1), "0");
        var due = PaymentDueStatusCalculator.calculate(payment, LocalDate.of(2026, 9, 10));

        assertThat(due.isOverdue()).isFalse();
        assertThat(due.isReminderEligible()).isFalse();
        assertThat(due.getOutstandingAmount()).isEqualByComparingTo("0");
    }

    @Test
    void futureDue_notOverdue() {
        SpacePaymentEntity payment = unpaid(LocalDate.of(2026, 9, 15), "10000");
        var due = PaymentDueStatusCalculator.calculate(payment, LocalDate.of(2026, 9, 5));

        assertThat(due.isOverdue()).isFalse();
        assertThat(due.isReminderEligible()).isFalse();
        assertThat(due.getDaysOverdue()).isZero();
    }

    @Test
    void rejectedStatus_reminderEligibleWhenOverdue() {
        SpacePaymentEntity payment = unpaid(LocalDate.of(2026, 9, 1), "10000");
        payment.setPaymentStatus(SpacePaymentStatus.REJECTED);
        var due = PaymentDueStatusCalculator.calculate(payment, LocalDate.of(2026, 9, 3));

        assertThat(due.isOverdue()).isTrue();
        assertThat(due.isReminderEligible()).isTrue();
    }

    private static SpacePaymentEntity unpaid(LocalDate dueDate, String amount) {
        return SpacePaymentEntity.builder()
                .dueDate(dueDate)
                .amount(new BigDecimal(amount))
                .paymentStatus(SpacePaymentStatus.PENDING)
                .member(MemberEntity.builder().isActive(true).fullName("Test").mobileNumber("9999999999").build())
                .build();
    }
}
