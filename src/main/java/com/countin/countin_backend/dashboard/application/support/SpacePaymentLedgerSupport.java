package com.countin.countin_backend.dashboard.application.support;

import com.countin.countin_backend.dashboard.domain.model.MemberPaymentStatus;
import com.countin.countin_backend.payment.domain.model.SpacePaymentStatus;
import com.countin.countin_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.countin.countin_backend.payment.infrastructure.persistence.repository.SpacePaymentRepository;
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
        if (payments == null || payments.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return payments.stream()
                .filter(payment -> payment.getPaymentStatus() == SpacePaymentStatus.PAID)
                .map(SpacePaymentEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public MemberPaymentStatus resolveWorkflowStatus(List<SpacePaymentEntity> payments) {
        if (payments == null || payments.isEmpty()) {
            return null;
        }
        if (payments.stream().anyMatch(payment -> payment.getPaymentStatus() == SpacePaymentStatus.UPDATE_REQUESTED)) {
            return MemberPaymentStatus.UPDATE_REQUESTED;
        }
        if (payments.stream().anyMatch(payment -> payment.getPaymentStatus() == SpacePaymentStatus.REJECTED)) {
            return MemberPaymentStatus.REJECTED;
        }
        if (payments.stream().anyMatch(payment -> payment.getPaymentStatus() == SpacePaymentStatus.UNDER_REVIEW
                || payment.getPaymentStatus() == SpacePaymentStatus.PROOF_UPLOADED)) {
            return MemberPaymentStatus.UNDER_REVIEW;
        }
        return null;
    }

    public MemberPaymentStatus resolveRowStatus(
            BigDecimal expected, BigDecimal collected, List<SpacePaymentEntity> payments) {
        MemberPaymentStatus workflowStatus = resolveWorkflowStatus(payments);
        if (workflowStatus != null) {
            return workflowStatus;
        }
        return deriveFinancialStatus(expected, collected);
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
}
