package com.acomi.acomi_backend.payment.application.service;

import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.common.web.PagedResponse;
import com.acomi.acomi_backend.payment.api.dto.response.OverduePaymentResponse;
import com.acomi.acomi_backend.payment.api.dto.response.OverduePaymentsPageResponse;
import com.acomi.acomi_backend.payment.application.support.PaymentDueStatusCalculator;
import com.acomi.acomi_backend.payment.application.support.PaymentDueStatusCalculator.DueStatus;
import com.acomi.acomi_backend.payment.domain.model.ReminderChannel;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentType;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.PaymentReminderDeliveryEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.PaymentReminderDeliveryRepository;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.SpacePaymentRepository;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OverduePaymentService {

    private final SpaceRepository spaceRepository;
    private final SpacePaymentRepository paymentRepository;
    private final PaymentReminderDeliveryRepository reminderDeliveryRepository;
    private final SpacePaymentAccessService accessService;

    @Transactional(readOnly = true)
    public OverduePaymentsPageResponse listOverdue(
            UUID spaceId,
            UUID callerId,
            SpacePaymentType paymentType,
            boolean reminderEligibleOnly,
            int page,
            int size) {
        accessService.requireManagePayments(spaceId, callerId);
        SpaceEntity space = loadSpace(spaceId);
        LocalDate businessDate = PaymentDueStatusCalculator.businessDate(space);

        Collection<SpacePaymentStatus> statuses = reminderEligibleOnly
                ? PaymentDueStatusCalculator.REMINDER_ELIGIBLE_STATUSES
                : List.of();
        boolean statusesEmpty = !reminderEligibleOnly;

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(
                safePage, safeSize, Sort.by(Sort.Order.asc("dueDate"), Sort.Order.asc("id")));

        Page<SpacePaymentEntity> candidates = paymentRepository.findOverdueCandidates(
                spaceId, businessDate, statuses, statusesEmpty, paymentType, pageable);

        Map<UUID, SpacePaymentEntity> graphById = paymentRepository
                .findAllByIdInWithMemberAndSpace(
                        candidates.getContent().stream().map(SpacePaymentEntity::getId).toList())
                .stream()
                .collect(Collectors.toMap(SpacePaymentEntity::getId, Function.identity(), (a, b) -> a));

        List<OverduePaymentResponse> rows = candidates.getContent().stream()
                .map(row -> graphById.getOrDefault(row.getId(), row))
                .map(entity -> toOverdueResponse(entity, space, businessDate))
                .filter(row -> row.isOverdue())
                .filter(row -> !reminderEligibleOnly || row.isReminderEligible())
                .toList();

        rows = enrichWithTodayReminderStatus(rows, businessDate);

        // Page metadata from DB candidates; in-memory filter is a safety net only.
        Page<OverduePaymentResponse> mapped = new org.springframework.data.domain.PageImpl<>(
                rows, pageable, candidates.getTotalElements());

        return OverduePaymentsPageResponse.builder()
                .businessDate(businessDate)
                .page(PagedResponse.from(mapped))
                .build();
    }

    /**
     * Reminder boundary: active overdue obligations eligible for future channels.
     * Does not send notifications.
     */
    @Transactional(readOnly = true)
    public List<OverduePaymentResponse> listReminderEligible(UUID spaceId, UUID callerId) {
        accessService.requireManagePayments(spaceId, callerId);
        return listReminderEligibleInternal(spaceId);
    }

    /**
     * System path for scheduler/reminder orchestration after access is already validated
     * (or running as trusted job). Paginates until all reminder-eligible rows are collected.
     */
    @Transactional(readOnly = true)
    public List<OverduePaymentResponse> listReminderEligibleInternal(UUID spaceId) {
        SpaceEntity space = loadSpace(spaceId);
        LocalDate businessDate = PaymentDueStatusCalculator.businessDate(space);
        Collection<SpacePaymentStatus> statuses = PaymentDueStatusCalculator.REMINDER_ELIGIBLE_STATUSES;

        List<OverduePaymentResponse> all = new java.util.ArrayList<>();
        int page = 0;
        int size = 100;
        org.springframework.data.domain.Page<SpacePaymentEntity> candidates;
        do {
            Pageable pageable = PageRequest.of(
                    page, size, Sort.by(Sort.Order.asc("dueDate"), Sort.Order.asc("id")));
            candidates = paymentRepository.findOverdueCandidates(
                    spaceId, businessDate, statuses, false, null, pageable);
            Map<UUID, SpacePaymentEntity> graphById = paymentRepository
                    .findAllByIdInWithMemberAndSpace(
                            candidates.getContent().stream().map(SpacePaymentEntity::getId).toList())
                    .stream()
                    .collect(Collectors.toMap(SpacePaymentEntity::getId, Function.identity(), (a, b) -> a));
            candidates.getContent().stream()
                    .map(row -> graphById.getOrDefault(row.getId(), row))
                    .map(entity -> toOverdueResponse(entity, space, businessDate))
                    .filter(OverduePaymentResponse::isReminderEligible)
                    .forEach(all::add);
            page++;
        } while (candidates.hasNext());
        return enrichWithTodayReminderStatus(all, businessDate);
    }

    public OverduePaymentResponse toOverdueResponse(
            SpacePaymentEntity entity, SpaceEntity space, LocalDate businessDate) {
        DueStatus due = PaymentDueStatusCalculator.calculate(entity, businessDate);
        return OverduePaymentResponse.builder()
                .paymentId(entity.getId())
                .spaceId(space.getId())
                .spaceName(space.getName())
                .memberId(entity.getMember().getId())
                .memberName(entity.getMember().getFullName())
                .memberMobile(entity.getMember().getMobileNumber())
                .paymentType(entity.getPaymentType())
                .paymentCategory(entity.getPaymentCategory())
                .title(entity.getTitle())
                .month(entity.getMonth())
                .billingPeriodStart(entity.getBillingPeriodStart())
                .billingPeriodEnd(entity.getBillingPeriodEnd())
                .dueDate(entity.getDueDate())
                .totalAmount(due.getTotalAmount())
                .paidAmount(due.getPaidAmount())
                .outstandingAmount(due.getOutstandingAmount())
                .currencyCode(entity.getCurrencyCode())
                .paymentStatus(entity.getPaymentStatus())
                .settlementStatus(due.getSettlementStatus())
                .overdue(due.isOverdue())
                .daysOverdue(due.getDaysOverdue())
                .reminderEligible(due.isReminderEligible())
                .build();
    }

    private List<OverduePaymentResponse> enrichWithTodayReminderStatus(
            List<OverduePaymentResponse> rows, LocalDate businessDate) {
        if (rows.isEmpty()) {
            return rows;
        }
        List<UUID> ids = rows.stream().map(OverduePaymentResponse::getPaymentId).toList();
        Map<UUID, PaymentReminderDeliveryEntity> byPayment = reminderDeliveryRepository
                .findByPaymentIdInAndBusinessDateAndChannel(ids, businessDate, ReminderChannel.WHATSAPP)
                .stream()
                .collect(Collectors.toMap(
                        PaymentReminderDeliveryEntity::getPaymentId, Function.identity(), (a, b) -> a));

        return rows.stream()
                .map(row -> {
                    PaymentReminderDeliveryEntity delivery = byPayment.get(row.getPaymentId());
                    if (delivery == null) {
                        return row;
                    }
                    return OverduePaymentResponse.builder()
                            .paymentId(row.getPaymentId())
                            .spaceId(row.getSpaceId())
                            .spaceName(row.getSpaceName())
                            .memberId(row.getMemberId())
                            .memberName(row.getMemberName())
                            .memberMobile(row.getMemberMobile())
                            .paymentType(row.getPaymentType())
                            .paymentCategory(row.getPaymentCategory())
                            .title(row.getTitle())
                            .month(row.getMonth())
                            .billingPeriodStart(row.getBillingPeriodStart())
                            .billingPeriodEnd(row.getBillingPeriodEnd())
                            .dueDate(row.getDueDate())
                            .totalAmount(row.getTotalAmount())
                            .paidAmount(row.getPaidAmount())
                            .outstandingAmount(row.getOutstandingAmount())
                            .currencyCode(row.getCurrencyCode())
                            .paymentStatus(row.getPaymentStatus())
                            .settlementStatus(row.getSettlementStatus())
                            .overdue(row.isOverdue())
                            .daysOverdue(row.getDaysOverdue())
                            .reminderEligible(row.isReminderEligible())
                            .reminderDeliveryStatusToday(delivery.getDeliveryStatus())
                            .reminderBusinessDate(delivery.getBusinessDate())
                            .reminderFailureReason(delivery.getFailureReason())
                            .build();
                })
                .toList();
    }

    private SpaceEntity loadSpace(UUID spaceId) {
        return spaceRepository
                .findById(spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Space", "id", spaceId));
    }
}
