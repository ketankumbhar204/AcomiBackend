package com.acomi.acomi_backend.member.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.member.application.support.MemberDepositSummary;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.repository.OccupancyRepository;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentCategory;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentType;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.SpacePaymentRepository;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberDepositLedgerTest {

    @Mock
    private OccupancyRepository occupancyRepository;

    @Mock
    private SpacePaymentRepository paymentRepository;

    @InjectMocks
    private MemberDepositLedger ledger;

    private UUID spaceId;
    private UUID memberId;
    private MemberEntity member;

    @BeforeEach
    void setUp() {
        spaceId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        SpaceEntity space = SpaceEntity.builder().name("Sunrise PG").build();
        space.setId(spaceId);
        member = MemberEntity.builder()
                .space(space)
                .depositAmount(BigDecimal.ZERO)
                .depositPaid(BigDecimal.ZERO)
                .depositRefunded(BigDecimal.ZERO)
                .build();
        member.setId(memberId);
    }

    @Test
    void summarize_readsContractDepositAndIgnoresUnreceivedPayment() {
        OccupancyEntity stay = OccupancyEntity.builder()
                .status(OccupancyStatus.ACTIVE)
                .depositSnapshot(new BigDecimal("3000.00"))
                .build();
        SpacePaymentEntity pending = security(SpacePaymentStatus.PENDING, "3000.00");
        when(occupancyRepository.findAllBySpaceIdAndMemberIdOrderByAllocatedAtDesc(spaceId, memberId))
                .thenReturn(List.of(stay));
        when(paymentRepository.findDepositLedgerRows(spaceId, memberId)).thenReturn(List.of(pending));

        MemberDepositSummary summary = ledger.summarize(member);

        assertThat(summary.getAmount()).isEqualByComparingTo("3000.00");
        assertThat(summary.getPaid()).isEqualByComparingTo("0");
        assertThat(summary.getBalance()).isEqualByComparingTo("0");
    }

    @Test
    void summarize_countsAReceivedDepositAndRecordedRefund() {
        OccupancyEntity stay = OccupancyEntity.builder()
                .status(OccupancyStatus.ACTIVE)
                .depositSnapshot(new BigDecimal("3000.00"))
                .build();
        member.setDepositRefunded(new BigDecimal("500.00"));
        when(occupancyRepository.findAllBySpaceIdAndMemberIdOrderByAllocatedAtDesc(spaceId, memberId))
                .thenReturn(List.of(stay));
        when(paymentRepository.findDepositLedgerRows(spaceId, memberId))
                .thenReturn(List.of(security(SpacePaymentStatus.PAID, "3000.00")));

        MemberDepositSummary summary = ledger.summarize(member);

        assertThat(summary.getPaid()).isEqualByComparingTo("3000.00");
        assertThat(summary.getRefunded()).isEqualByComparingTo("500.00");
        assertThat(summary.getBalance()).isEqualByComparingTo("2500.00");
    }

    @Test
    void assertManualPaidAllowed_rejectsAPaidFigureThatTheLedgerHasNotReceived() {
        when(paymentRepository.findDepositLedgerRows(spaceId, memberId))
                .thenReturn(List.of(security(SpacePaymentStatus.PENDING, "3000.00")));

        assertThatThrownBy(() -> ledger.assertManualPaidAllowed(member, new BigDecimal("3000.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("received security deposit payments");
    }

    @Test
    void syncContractDeposit_updatesTheOpenSecurityPaymentAndLeavesReceivedPayments() {
        OccupancyEntity stay = OccupancyEntity.builder()
                .status(OccupancyStatus.ACTIVE)
                .depositSnapshot(new BigDecimal("3000.00"))
                .build();
        SpacePaymentEntity open = security(SpacePaymentStatus.PENDING, "3000.00");
        open.setBaseAmount(new BigDecimal("3000.00"));
        when(occupancyRepository.findAllBySpaceIdAndMemberIdOrderByAllocatedAtDesc(spaceId, memberId))
                .thenReturn(List.of(stay));
        when(paymentRepository.findDepositLedgerRows(spaceId, memberId)).thenReturn(List.of(open));

        ledger.syncContractDeposit(member, new BigDecimal("4000.00"));

        assertThat(stay.getDepositSnapshot()).isEqualByComparingTo("4000.00");
        assertThat(open.getAmount()).isEqualByComparingTo("4000.00");
        assertThat(open.getBaseAmount()).isEqualByComparingTo("4000.00");
        verify(occupancyRepository).save(stay);
        verify(paymentRepository).save(open);
    }

    private static SpacePaymentEntity security(SpacePaymentStatus status, String amount) {
        return SpacePaymentEntity.builder()
                .paymentType(SpacePaymentType.DEPOSIT)
                .paymentCategory(SpacePaymentCategory.SECURITY)
                .paymentStatus(status)
                .amount(new BigDecimal(amount))
                .build();
    }
}
