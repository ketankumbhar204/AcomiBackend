package com.acomi.acomi_backend.member.application.service;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.member.api.dto.response.MemberDetailsResponse;
import com.acomi.acomi_backend.member.application.support.MemberDepositSummary;
import com.acomi.acomi_backend.member.application.support.MemberDepositSummaryCalculator;
import com.acomi.acomi_backend.member.application.support.MemberDepositSummaryCalculator.DepositPayment;
import com.acomi.acomi_backend.member.application.support.MemberDepositSummaryCalculator.StayDeposit;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.repository.OccupancyRepository;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentCategory;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentType;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.SpacePaymentRepository;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Loads the stay contract and deposit payments that back the member deposit summary.
 */
@Service
@RequiredArgsConstructor
public class MemberDepositLedger {

    private static final Set<SpacePaymentStatus> LOCKED_PAYMENT_STATUSES = EnumSet.of(
            SpacePaymentStatus.PAID,
            SpacePaymentStatus.UNDER_REVIEW,
            SpacePaymentStatus.PROOF_UPLOADED);

    private final OccupancyRepository occupancyRepository;
    private final SpacePaymentRepository paymentRepository;

    public MemberDepositSummary summarize(MemberEntity member) {
        List<StayDeposit> stays = loadStays(member).stream()
                .map(stay -> new StayDeposit(stay.getStatus(), stay.getDepositSnapshot()))
                .toList();
        List<SpacePaymentEntity> rows = loadRows(member);
        List<DepositPayment> payments = rows.stream().map(MemberDepositLedger::toAllocation).toList();
        return MemberDepositSummaryCalculator.summarize(
                stays,
                payments,
                member.getDepositAmount(),
                member.getDepositPaid(),
                member.getDepositRefunded());
    }

    public MemberDetailsResponse apply(MemberEntity member, MemberDetailsResponse response) {
        MemberDepositSummary summary = summarize(member);
        return response.toBuilder()
                .depositAmount(summary.getAmount())
                .depositPaid(summary.getPaid())
                .depositRefunded(summary.getRefunded())
                .depositBalance(summary.getBalance())
                .build();
    }

    /**
     * Manual edits cannot mark a deposit received while a security-deposit payment exists.
     * Received money is whatever those payments have actually been marked PAID.
     */
    public void assertManualPaidAllowed(MemberEntity member, BigDecimal requestedPaid) {
        List<DepositPayment> payments = loadRows(member).stream()
                .map(MemberDepositLedger::toAllocation)
                .toList();
        if (!MemberDepositSummaryCalculator.hasSecurityDepositPayment(payments)) {
            return;
        }
        BigDecimal received = MemberDepositSummaryCalculator.resolvePaid(payments, BigDecimal.ZERO);
        if (received.compareTo(requestedPaid == null ? BigDecimal.ZERO : requestedPaid) != 0) {
            throw new BusinessException(
                    "Amount paid follows received security deposit payments", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Keeps the active or reserved contract, and a single still-open security-deposit payment,
     * aligned with an edited deposit amount. Received payments are left unchanged.
     */
    public void syncContractDeposit(MemberEntity member, BigDecimal depositAmount) {
        if (depositAmount == null) {
            return;
        }
        OccupancyEntity stay = currentStay(loadStays(member));
        if (stay == null) {
            return;
        }
        stay.setDepositSnapshot(depositAmount);
        occupancyRepository.save(stay);

        List<SpacePaymentEntity> openSecurity = loadRows(member).stream()
                .filter(MemberDepositLedger::isOpenSecurityDeposit)
                .toList();
        if (openSecurity.size() != 1) {
            return;
        }
        SpacePaymentEntity payment = openSecurity.get(0);
        payment.setAmount(depositAmount);
        if (payment.getBaseAmount() != null) {
            payment.setBaseAmount(depositAmount);
        }
        if (payment.getConfiguredMonthlyAmount() != null) {
            payment.setConfiguredMonthlyAmount(depositAmount);
        }
        paymentRepository.save(payment);
    }

    private List<OccupancyEntity> loadStays(MemberEntity member) {
        UUID spaceId = member.getSpace().getId();
        return occupancyRepository.findAllBySpaceIdAndMemberIdOrderByAllocatedAtDesc(spaceId, member.getId());
    }

    private List<SpacePaymentEntity> loadRows(MemberEntity member) {
        UUID spaceId = member.getSpace().getId();
        return paymentRepository.findDepositLedgerRows(spaceId, member.getId());
    }

    private static OccupancyEntity currentStay(List<OccupancyEntity> stays) {
        for (OccupancyEntity stay : stays) {
            if (stay.getStatus() == OccupancyStatus.ACTIVE) {
                return stay;
            }
        }
        for (OccupancyEntity stay : stays) {
            if (stay.getStatus() == OccupancyStatus.RESERVED) {
                return stay;
            }
        }
        return null;
    }

    private static boolean isOpenSecurityDeposit(SpacePaymentEntity payment) {
        return payment.getPaymentType() == SpacePaymentType.DEPOSIT
                && payment.getPaymentCategory() == SpacePaymentCategory.SECURITY
                && !LOCKED_PAYMENT_STATUSES.contains(payment.getPaymentStatus());
    }

    private static DepositPayment toAllocation(SpacePaymentEntity payment) {
        return new DepositPayment(
                payment.getPaymentType(),
                payment.getPaymentCategory(),
                payment.getPaymentStatus(),
                payment.getAmount());
    }
}
