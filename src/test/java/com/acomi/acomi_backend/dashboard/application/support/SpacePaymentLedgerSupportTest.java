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
        assertThat(support.resolveRowStatus(
                        new BigDecimal("10000"), new BigDecimal("4000"), BigDecimal.ZERO, List.of(paid)))
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

    private SpacePaymentEntity payment(SpacePaymentStatus status, String amount) {
        return SpacePaymentEntity.builder()
                .paymentType(SpacePaymentType.RENT)
                .paymentCategory(SpacePaymentCategory.MONTHLY)
                .paymentStatus(status)
                .amount(new BigDecimal(amount))
                .build();
    }
}
