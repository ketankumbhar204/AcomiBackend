package com.countin.countin_backend.dashboard.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.countin.countin_backend.dashboard.domain.model.MemberPaymentStatus;
import com.countin.countin_backend.payment.domain.model.SpacePaymentCategory;
import com.countin.countin_backend.payment.domain.model.SpacePaymentStatus;
import com.countin.countin_backend.payment.domain.model.SpacePaymentType;
import com.countin.countin_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.countin.countin_backend.payment.infrastructure.persistence.repository.SpacePaymentRepository;
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
    void prioritizesWorkflowStatusOverPendingFinancialState() {
        SpacePaymentEntity underReview = payment(SpacePaymentStatus.UNDER_REVIEW, "10000");
        assertThat(support.resolveRowStatus(new BigDecimal("10000"), BigDecimal.ZERO, List.of(underReview)))
                .isEqualTo(MemberPaymentStatus.UNDER_REVIEW);
    }

    @Test
    void marksPaidWhenRentApproved() {
        SpacePaymentEntity paid = payment(SpacePaymentStatus.PAID, "10000");
        assertThat(support.resolveRowStatus(new BigDecimal("10000"), new BigDecimal("10000"), List.of(paid)))
                .isEqualTo(MemberPaymentStatus.PAID);
    }

    @Test
    void exposesChangesRequestedQueueStatus() {
        SpacePaymentEntity updateRequested = payment(SpacePaymentStatus.UPDATE_REQUESTED, "10000");
        assertThat(support.resolveWorkflowStatus(List.of(updateRequested)))
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
