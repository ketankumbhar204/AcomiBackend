package com.acomi.acomi_backend.payment.application.service;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.occupancy.api.dto.request.ReservationPaymentSettlementDecision;
import com.acomi.acomi_backend.occupancy.api.dto.response.ReservationCancellationPreviewResponse;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.acomi.acomi_backend.payment.api.dto.request.CancelSpacePaymentRequest;
import com.acomi.acomi_backend.payment.api.dto.request.SettleSpacePaymentRequest;
import com.acomi.acomi_backend.payment.api.dto.response.PaymentSettlementResponse;
import com.acomi.acomi_backend.payment.api.dto.response.SpacePaymentResponse;
import com.acomi.acomi_backend.payment.domain.model.PaymentFinancialDisposition;
import com.acomi.acomi_backend.payment.domain.model.PaymentSettlementAction;
import com.acomi.acomi_backend.payment.domain.model.PaymentTimelineEventType;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentCategory;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentMethod;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentType;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.PaymentSettlementEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.PaymentSettlementRepository;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.SpacePaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentSettlementService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(PaymentSettlementService.class);

    private static final Set<SpacePaymentStatus> UNPAID_CANCELLABLE = EnumSet.of(
            SpacePaymentStatus.PENDING,
            SpacePaymentStatus.PROOF_UPLOADED,
            SpacePaymentStatus.UNDER_REVIEW,
            SpacePaymentStatus.UPDATE_REQUESTED,
            SpacePaymentStatus.REJECTED);

    private final SpacePaymentRepository paymentRepository;
    private final PaymentSettlementRepository settlementRepository;
    private final SpacePaymentTimelineService timelineService;
    private final SpacePaymentAccessService accessService;
    private final PaymentMonthSnapshotService snapshotService;

    @Transactional(readOnly = true)
    public ReservationCancellationPreviewResponse previewReservationCancellation(
            UUID spaceId, OccupancyEntity occupancy, UUID callerId) {
        accessService.requireManagePayments(spaceId, callerId);
        List<SpacePaymentEntity> obligations = reservationObligations(occupancy);
        Map<UUID, SettlementTotals> totals = loadTotals(obligations);
        List<ReservationCancellationPreviewResponse.Item> items = new ArrayList<>();
        boolean requiresCollected = false;
        for (SpacePaymentEntity payment : obligations) {
            SettlementTotals t = totals.getOrDefault(payment.getId(), SettlementTotals.zero());
            boolean collected = payment.getPaymentStatus() == SpacePaymentStatus.PAID;
            BigDecimal refundable = collected
                    ? payment.getAmount().subtract(t.refunded).subtract(t.retained).max(BigDecimal.ZERO)
                    : BigDecimal.ZERO;
            PaymentFinancialDisposition disposition = resolveDisposition(payment, t);
            List<PaymentSettlementAction> allowed = new ArrayList<>();
            if (!collected && payment.getPaymentStatus() != SpacePaymentStatus.CANCELLED) {
                allowed.add(PaymentSettlementAction.CANCEL_UNPAID);
            } else if (collected && refundable.signum() > 0) {
                requiresCollected = true;
                allowed.add(PaymentSettlementAction.FULL_REFUND);
                allowed.add(PaymentSettlementAction.PARTIAL_REFUND);
                allowed.add(PaymentSettlementAction.RETAIN);
            }
            items.add(ReservationCancellationPreviewResponse.Item.builder()
                    .paymentId(payment.getId())
                    .title(payment.getTitle())
                    .paymentType(payment.getPaymentType())
                    .paymentCategory(payment.getPaymentCategory())
                    .amount(payment.getAmount())
                    .currencyCode(payment.getCurrencyCode())
                    .paymentStatus(payment.getPaymentStatus())
                    .collected(collected)
                    .refundableAmount(refundable)
                    .alreadyRefundedAmount(t.refunded)
                    .alreadyRetainedAmount(t.retained)
                    .disposition(disposition)
                    .allowedActions(allowed)
                    .build());
        }
        return ReservationCancellationPreviewResponse.builder()
                .occupancyId(occupancy.getId())
                .memberId(occupancy.getMember() != null ? occupancy.getMember().getId() : null)
                .memberName(occupancy.getMember() != null ? occupancy.getMember().getFullName() : null)
                .payments(items)
                .requiresCollectedSettlement(requiresCollected)
                .build();
    }

    /**
     * Applies settlement decisions then auto-cancels any remaining unpaid reservation obligations.
     * Does not close the occupancy — caller does that after this returns.
     */
    @Transactional
    public void settleReservationCancellation(
            UUID spaceId,
            OccupancyEntity occupancy,
            UUID callerId,
            List<ReservationPaymentSettlementDecision> decisions,
            String defaultReason) {
        accessService.requireManagePayments(spaceId, callerId);
        List<SpacePaymentEntity> obligations = reservationObligations(occupancy);
        Map<UUID, SpacePaymentEntity> byId = obligations.stream()
                .collect(Collectors.toMap(SpacePaymentEntity::getId, p -> p, (a, b) -> a));
        Map<UUID, SettlementTotals> totals = loadTotals(obligations);

        Set<UUID> decided = new HashSet<>();
        List<ReservationPaymentSettlementDecision> safeDecisions =
                decisions == null ? List.of() : decisions;
        for (ReservationPaymentSettlementDecision decision : safeDecisions) {
            if (decision == null || decision.getPaymentId() == null || decision.getAction() == null) {
                throw new BusinessException("Each settlement requires paymentId and action", HttpStatus.BAD_REQUEST);
            }
            SpacePaymentEntity payment = byId.get(decision.getPaymentId());
            if (payment == null) {
                throw new BusinessException(
                        "Payment is not part of this reservation", HttpStatus.BAD_REQUEST);
            }
            if (payment.getPaymentCategory() == SpacePaymentCategory.REFUND) {
                throw new BusinessException("Cannot settle a refund row directly", HttpStatus.BAD_REQUEST);
            }
            // Reservation cancel must fully settle remaining collected balance (refund and/or retain).
            applyDecision(
                    payment,
                    occupancy,
                    callerId,
                    decision,
                    defaultReason,
                    totals,
                    true,
                    null,
                    null,
                    null);
            decided.add(payment.getId());
        }

        // Collected payments with remaining balance must have an explicit decision.
        for (SpacePaymentEntity payment : obligations) {
            if (payment.getPaymentStatus() != SpacePaymentStatus.PAID) {
                continue;
            }
            SettlementTotals t = totals.getOrDefault(payment.getId(), SettlementTotals.zero());
            BigDecimal remaining =
                    payment.getAmount().subtract(t.refunded).subtract(t.retained).max(BigDecimal.ZERO);
            if (remaining.signum() > 0 && !decided.contains(payment.getId())) {
                throw new BusinessException(
                        "SETTLEMENT_REQUIRED",
                        "Select refund or retain for collected payment: " + payment.getTitle(),
                        HttpStatus.BAD_REQUEST);
            }
        }

        // Auto-cancel unpaid obligations (including those without an explicit CANCEL decision).
        for (SpacePaymentEntity payment : obligations) {
            if (payment.getPaymentStatus() == SpacePaymentStatus.CANCELLED) {
                continue;
            }
            if (payment.getPaymentStatus() == SpacePaymentStatus.PAID) {
                continue;
            }
            if (payment.getPaymentCategory() == SpacePaymentCategory.REFUND) {
                continue;
            }
            if (!UNPAID_CANCELLABLE.contains(payment.getPaymentStatus())) {
                continue;
            }
            cancelUnpaidInternal(
                    payment,
                    occupancy,
                    callerId,
                    defaultReason != null && !defaultReason.isBlank()
                            ? defaultReason
                            : "Reservation cancelled");
        }

        refreshSnapshots(obligations, spaceId, callerId);
    }

    @Transactional
    public SpacePaymentResponse cancelUnpaidPayment(
            UUID spaceId, UUID paymentId, UUID callerId, CancelSpacePaymentRequest request) {
        accessService.requireManagePayments(spaceId, callerId);
        SpacePaymentEntity payment = loadPayment(spaceId, paymentId);
        if (payment.getPaymentCategory() == SpacePaymentCategory.REFUND) {
            throw new BusinessException("Refund rows cannot be cancelled this way", HttpStatus.BAD_REQUEST);
        }
        if (payment.getPaymentStatus() == SpacePaymentStatus.CANCELLED) {
            return SpacePaymentResponse.from(payment);
        }
        if (payment.getPaymentStatus() == SpacePaymentStatus.PAID
                || payment.getPaymentStatus() == SpacePaymentStatus.REFUNDED) {
            throw new BusinessException(
                    "PAID_REQUIRES_SETTLEMENT",
                    "Collected payments must be refunded or retained — they cannot be cancelled",
                    HttpStatus.BAD_REQUEST);
        }
        if (!UNPAID_CANCELLABLE.contains(payment.getPaymentStatus())) {
            throw new BusinessException(
                    "Payment cannot be cancelled in status " + payment.getPaymentStatus(),
                    HttpStatus.BAD_REQUEST);
        }
        String reason = request != null && request.getReason() != null && !request.getReason().isBlank()
                ? request.getReason().trim()
                : "Payment cancelled by owner/manager";
        cancelUnpaidInternal(payment, payment.getOccupancy(), callerId, reason);
        refreshSnapshot(payment, spaceId, callerId);
        return SpacePaymentResponse.from(payment);
    }

    @Transactional
    public SpacePaymentResponse settleCollectedPayment(
            UUID spaceId, UUID paymentId, UUID callerId, SettleSpacePaymentRequest request) {
        accessService.requireManagePayments(spaceId, callerId);
        if (request == null || request.getAction() == null) {
            throw new BusinessException("Settlement action is required", HttpStatus.BAD_REQUEST);
        }
        if (request.getAction() == PaymentSettlementAction.CANCEL_UNPAID) {
            throw new BusinessException(
                    "Use cancel endpoint for unpaid obligations", HttpStatus.BAD_REQUEST);
        }
        SpacePaymentEntity payment = loadPayment(spaceId, paymentId);
        if (payment.getPaymentStatus() == SpacePaymentStatus.REFUNDED) {
            throw new BusinessException(
                    "ALREADY_REFUNDED",
                    "Payment is already fully refunded",
                    HttpStatus.CONFLICT);
        }
        if (payment.getPaymentStatus() != SpacePaymentStatus.PAID) {
            throw new BusinessException(
                    "Only verified collected (PAID) payments can be refunded or retained. "
                            + "UNDER_REVIEW is not refundable until collection is confirmed.",
                    HttpStatus.BAD_REQUEST);
        }
        ReservationPaymentSettlementDecision decision = new ReservationPaymentSettlementDecision();
        decision.setPaymentId(paymentId);
        decision.setAction(request.getAction());
        decision.setRefundAmount(request.getRefundAmount());
        decision.setRetainedAmount(request.getRetainedAmount());
        decision.setReason(request.getReason());
        Map<UUID, SettlementTotals> totals = loadTotals(List.of(payment));
        // Payment-level partial refund must not auto-retain the remainder.
        applyDecision(
                payment,
                payment.getOccupancy(),
                callerId,
                decision,
                null,
                totals,
                false,
                request.getRefundMethod(),
                request.getRefundDate(),
                request.getRefundReference());
        refreshSnapshot(payment, spaceId, callerId);
        return SpacePaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentSettlementResponse> listSettlements(UUID spaceId, UUID paymentId, UUID callerId) {
        accessService.requireManagePayments(spaceId, callerId);
        loadPayment(spaceId, paymentId);
        return settlementRepository.findByPayment_IdOrderByPerformedAtAsc(paymentId).stream()
                .map(PaymentSettlementResponse::from)
                .toList();
    }

    public PaymentFinancialDisposition resolveDisposition(
            SpacePaymentEntity payment, SettlementTotals totals) {
        if (payment.getPaymentStatus() == SpacePaymentStatus.CANCELLED) {
            return PaymentFinancialDisposition.CANCELLED_UNPAID;
        }
        if (payment.getPaymentStatus() == SpacePaymentStatus.REFUNDED) {
            return PaymentFinancialDisposition.FULLY_REFUNDED;
        }
        if (payment.getPaymentStatus() != SpacePaymentStatus.PAID) {
            return PaymentFinancialDisposition.OPEN;
        }
        BigDecimal amount = money(payment.getAmount());
        BigDecimal refunded = money(totals.refunded);
        BigDecimal retained = money(totals.retained);
        if (refunded.signum() <= 0 && retained.signum() <= 0) {
            return PaymentFinancialDisposition.COLLECTED_UNSETTLED;
        }
        if (refunded.compareTo(amount) >= 0) {
            return PaymentFinancialDisposition.FULLY_REFUNDED;
        }
        if (retained.compareTo(amount) >= 0) {
            return PaymentFinancialDisposition.FULLY_RETAINED;
        }
        if (refunded.add(retained).compareTo(amount) >= 0) {
            return refunded.signum() > 0
                    ? PaymentFinancialDisposition.PARTIALLY_REFUNDED
                    : PaymentFinancialDisposition.FULLY_RETAINED;
        }
        return PaymentFinancialDisposition.PARTIALLY_REFUNDED;
    }

    private void applyDecision(
            SpacePaymentEntity payment,
            OccupancyEntity occupancy,
            UUID callerId,
            ReservationPaymentSettlementDecision decision,
            String defaultReason,
            Map<UUID, SettlementTotals> totals,
            boolean autoRetainRemainder,
            SpacePaymentMethod refundMethod,
            LocalDate refundDate,
            String refundReference) {
        PaymentSettlementAction action = decision.getAction();
        String reason = firstNonBlank(decision.getReason(), defaultReason, "Reservation settlement");

        if (action == PaymentSettlementAction.CANCEL_UNPAID) {
            if (payment.getPaymentStatus() == SpacePaymentStatus.PAID
                    || payment.getPaymentStatus() == SpacePaymentStatus.REFUNDED) {
                throw new BusinessException(
                        "Cannot cancel a collected payment — choose refund or retain",
                        HttpStatus.BAD_REQUEST);
            }
            cancelUnpaidInternal(payment, occupancy, callerId, reason);
            totals.put(payment.getId(), SettlementTotals.zero());
            return;
        }

        if (payment.getPaymentStatus() == SpacePaymentStatus.REFUNDED) {
            throw new BusinessException(
                    "ALREADY_REFUNDED", "Payment is already fully refunded", HttpStatus.CONFLICT);
        }
        if (payment.getPaymentStatus() != SpacePaymentStatus.PAID) {
            throw new BusinessException(
                    "Refund/retain applies only to verified collected (PAID) payments",
                    HttpStatus.BAD_REQUEST);
        }

        SettlementTotals current = totals.getOrDefault(payment.getId(), SettlementTotals.zero());
        BigDecimal remaining =
                money(payment.getAmount()).subtract(current.refunded).subtract(current.retained);
        if (remaining.signum() <= 0) {
            throw new BusinessException(
                    "Payment is already fully settled", HttpStatus.CONFLICT);
        }

        BigDecimal refundAmount = BigDecimal.ZERO;
        BigDecimal retainAmount = BigDecimal.ZERO;

        switch (action) {
            case FULL_REFUND -> {
                refundAmount = remaining;
                retainAmount = BigDecimal.ZERO;
            }
            case RETAIN -> {
                retainAmount = decision.getRetainedAmount() != null
                        ? money(decision.getRetainedAmount())
                        : remaining;
                if (retainAmount.signum() <= 0) {
                    throw new BusinessException("Retained amount must be greater than zero", HttpStatus.BAD_REQUEST);
                }
                if (retainAmount.compareTo(remaining) > 0) {
                    throw new BusinessException(
                            "Retained amount cannot exceed the unsettled collected balance",
                            HttpStatus.BAD_REQUEST);
                }
                refundAmount = BigDecimal.ZERO;
            }
            case PARTIAL_REFUND -> {
                refundAmount = money(decision.getRefundAmount());
                if (refundAmount.signum() <= 0) {
                    throw new BusinessException("Refund amount is required for partial refund", HttpStatus.BAD_REQUEST);
                }
                if (decision.getRetainedAmount() != null) {
                    retainAmount = money(decision.getRetainedAmount());
                } else if (autoRetainRemainder) {
                    retainAmount = remaining.subtract(refundAmount);
                } else {
                    retainAmount = BigDecimal.ZERO;
                }
                if (retainAmount.signum() < 0) {
                    retainAmount = BigDecimal.ZERO;
                }
                if (refundAmount.add(retainAmount).compareTo(remaining) > 0) {
                    throw new BusinessException(
                            "Refund + retained cannot exceed the unsettled collected balance",
                            HttpStatus.BAD_REQUEST);
                }
            }
            default -> throw new BusinessException("Unsupported settlement action", HttpStatus.BAD_REQUEST);
        }

        if (refundAmount.add(retainAmount).compareTo(remaining) > 0) {
            throw new BusinessException(
                    "Refund + retained cannot exceed the unsettled collected balance",
                    HttpStatus.BAD_REQUEST);
        }

        LocalDate effectiveRefundDate = refundDate != null ? refundDate : LocalDate.now();
        SpacePaymentEntity refundRow = null;
        if (refundAmount.signum() > 0) {
            refundRow = createRefundPayment(
                    payment, refundAmount, reason, callerId, refundMethod, effectiveRefundDate, refundReference);
            timelineService.record(
                    payment,
                    PaymentTimelineEventType.REFUNDED,
                    "Refunded " + refundAmount.toPlainString() + (reason != null ? " — " + reason : ""),
                    callerId);
        }
        if (retainAmount.signum() > 0) {
            timelineService.record(
                    payment,
                    PaymentTimelineEventType.RETAINED,
                    "Retained " + retainAmount.toPlainString() + (reason != null ? " — " + reason : ""),
                    callerId);
        }

        PaymentSettlementAction recordedAction = action;
        if (action == PaymentSettlementAction.PARTIAL_REFUND
                && refundAmount.compareTo(remaining) == 0
                && retainAmount.signum() == 0) {
            recordedAction = PaymentSettlementAction.FULL_REFUND;
        } else if (action == PaymentSettlementAction.PARTIAL_REFUND
                && refundAmount.signum() == 0
                && retainAmount.compareTo(remaining) == 0) {
            recordedAction = PaymentSettlementAction.RETAIN;
        }

        PaymentSettlementEntity settlement = PaymentSettlementEntity.builder()
                .space(payment.getSpace())
                .payment(payment)
                .occupancy(occupancy != null ? occupancy : payment.getOccupancy())
                .action(recordedAction)
                .refundAmount(refundAmount)
                .retainedAmount(retainAmount)
                .reason(reason)
                .refundPayment(refundRow)
                .performedBy(callerId)
                .performedAt(LocalDateTime.now())
                .refundMethod(refundMethod)
                .refundReference(trimToNull(refundReference))
                .refundDate(refundAmount.signum() > 0 ? effectiveRefundDate : null)
                .processingStatus("COMPLETED")
                .build();
        settlementRepository.save(settlement);

        SettlementTotals updated = new SettlementTotals(
                current.refunded.add(refundAmount), current.retained.add(retainAmount));
        totals.put(payment.getId(), updated);

        payment.setRefundedAmount(updated.refunded);
        payment.setRetainedAmount(updated.retained);
        // Only full cash return becomes REFUNDED. Retained money is not a refund.
        if (updated.refunded.compareTo(money(payment.getAmount())) >= 0) {
            payment.setPaymentStatus(SpacePaymentStatus.REFUNDED);
        }
        paymentRepository.save(payment);
    }

    private void cancelUnpaidInternal(
            SpacePaymentEntity payment, OccupancyEntity occupancy, UUID callerId, String reason) {
        if (payment.getPaymentStatus() == SpacePaymentStatus.CANCELLED) {
            return;
        }
        if (payment.getPaymentStatus() == SpacePaymentStatus.PAID) {
            throw new BusinessException(
                    "Collected payments cannot be cancelled", HttpStatus.BAD_REQUEST);
        }
        payment.setPaymentStatus(SpacePaymentStatus.CANCELLED);
        payment.setRejectionReason(reason);
        String prior = payment.getRemarks();
        payment.setRemarks(
                (prior == null || prior.isBlank() ? "" : prior + " | ") + "Cancelled: " + reason);
        paymentRepository.save(payment);
        timelineService.record(payment, PaymentTimelineEventType.CANCELLED, reason, callerId);

        PaymentSettlementEntity settlement = PaymentSettlementEntity.builder()
                .space(payment.getSpace())
                .payment(payment)
                .occupancy(occupancy != null ? occupancy : payment.getOccupancy())
                .action(PaymentSettlementAction.CANCEL_UNPAID)
                .refundAmount(BigDecimal.ZERO)
                .retainedAmount(BigDecimal.ZERO)
                .reason(reason)
                .performedBy(callerId)
                .performedAt(LocalDateTime.now())
                .build();
        settlementRepository.save(settlement);
    }

    private SpacePaymentEntity createRefundPayment(
            SpacePaymentEntity original,
            BigDecimal amount,
            String reason,
            UUID callerId,
            SpacePaymentMethod refundMethod,
            LocalDate refundDate,
            String refundReference) {
        LocalDate effectiveDate = refundDate != null ? refundDate : LocalDate.now();
        SpacePaymentType refundType = original.getPaymentType() == SpacePaymentType.DEPOSIT
                ? SpacePaymentType.DEPOSIT
                : original.getPaymentType() == SpacePaymentType.RENT
                        ? SpacePaymentType.RENT
                        : SpacePaymentType.OTHER;
        SpacePaymentEntity refund = SpacePaymentEntity.builder()
                .space(original.getSpace())
                .member(original.getMember())
                .occupancy(original.getOccupancy())
                .paymentType(refundType)
                .paymentCategory(SpacePaymentCategory.REFUND)
                .title(refundTitleFor(original))
                .description("Refund against payment " + original.getId())
                .amount(amount)
                .currencyCode(original.getCurrencyCode() != null ? original.getCurrencyCode() : "INR")
                .dueDate(effectiveDate)
                .month(original.getMonth() != null ? original.getMonth() : effectiveDate.toString().substring(0, 7))
                .paymentStatus(SpacePaymentStatus.PAID)
                .paymentDate(effectiveDate)
                .paymentMethod(refundMethod != null ? refundMethod : original.getPaymentMethod())
                .referenceNumber(trimToNull(refundReference))
                .remarks(reason)
                .targetLabel(original.getTargetLabel())
                .relatedPayment(original)
                .baseAmount(amount)
                .taxAmount(BigDecimal.ZERO)
                .taxEnabled(false)
                .prorated(false)
                .build();
        refund = paymentRepository.save(refund);
        timelineService.record(
                refund,
                PaymentTimelineEventType.CREATED,
                "Refund recorded against " + original.getId(),
                callerId);
        timelineService.record(refund, PaymentTimelineEventType.PAID, "Refund processed", callerId);
        return refund;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Avoid "Refund — Refund — Rent…" when the source title already starts with Refund. */
    private static String refundTitleFor(SpacePaymentEntity original) {
        String base = nullToEmpty(original.getTitle()).trim();
        while (base.matches("(?i)^refund\\s*[—\\-–·].*")) {
            base = base.replaceFirst("(?i)^refund\\s*[—\\-–·]\\s*", "").trim();
        }
        if (base.isEmpty()) {
            return "Refund";
        }
        return "Refund — " + base;
    }

    private List<SpacePaymentEntity> reservationObligations(OccupancyEntity occupancy) {
        if (occupancy == null || occupancy.getId() == null) {
            return List.of();
        }
        return paymentRepository.findByOccupancy_Id(occupancy.getId()).stream()
                .filter(p -> p.getPaymentCategory() != SpacePaymentCategory.REFUND)
                .toList();
    }

    private Map<UUID, SettlementTotals> loadTotals(List<SpacePaymentEntity> payments) {
        Map<UUID, SettlementTotals> map = new HashMap<>();
        if (payments == null || payments.isEmpty()) {
            return map;
        }
        List<UUID> ids = payments.stream().map(SpacePaymentEntity::getId).filter(Objects::nonNull).toList();
        for (UUID id : ids) {
            map.put(id, SettlementTotals.zero());
        }
        List<PaymentSettlementEntity> rows =
                settlementRepository.findByPayment_IdInOrderByPerformedAtAsc(ids);
        for (PaymentSettlementEntity row : rows) {
            UUID id = row.getPayment().getId();
            SettlementTotals current = map.getOrDefault(id, SettlementTotals.zero());
            map.put(
                    id,
                    new SettlementTotals(
                            current.refunded.add(money(row.getRefundAmount())),
                            current.retained.add(money(row.getRetainedAmount()))));
        }
        return map;
    }

    private SpacePaymentEntity loadPayment(UUID spaceId, UUID paymentId) {
        return paymentRepository
                .findByIdAndSpaceId(paymentId, spaceId)
                .orElseThrow(() -> new BusinessException("Payment not found", HttpStatus.NOT_FOUND));
    }

    private void refreshSnapshot(SpacePaymentEntity payment, UUID spaceId, UUID callerId) {
        try {
            paymentRepository.flush();
            snapshotService.refreshAfterMutation(spaceId, callerId, payment.getMonth());
        } catch (RuntimeException ex) {
            // Snapshot refresh must not roll back settlement, but must not fail silently.
            log.warn(
                    "payment_snapshot_refresh_failed spaceId={} paymentId={} month={} reason={}",
                    spaceId,
                    payment != null ? payment.getId() : null,
                    payment != null ? payment.getMonth() : null,
                    ex.toString());
        }
    }

    private void refreshSnapshots(List<SpacePaymentEntity> payments, UUID spaceId, UUID callerId) {
        Set<String> months = payments.stream()
                .map(SpacePaymentEntity::getMonth)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        try {
            paymentRepository.flush();
            for (String month : months) {
                snapshotService.refreshAfterMutation(spaceId, callerId, month);
            }
        } catch (RuntimeException ex) {
            log.warn(
                    "payment_snapshot_refresh_failed spaceId={} months={} reason={}",
                    spaceId,
                    months,
                    ex.toString());
        }
    }

    /** Recompute month snapshot after occupancy status changes (e.g. reservation → vacated). */
    public void refreshMonthSnapshot(UUID spaceId, UUID callerId, String month) {
        if (month == null || month.isBlank()) {
            return;
        }
        try {
            paymentRepository.flush();
            snapshotService.refreshAfterMutation(spaceId, callerId, month);
        } catch (RuntimeException ex) {
            log.warn(
                    "payment_snapshot_refresh_failed spaceId={} month={} reason={}",
                    spaceId,
                    month,
                    ex.toString());
        }
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    public record SettlementTotals(BigDecimal refunded, BigDecimal retained) {
        static SettlementTotals zero() {
            return new SettlementTotals(BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }
}
