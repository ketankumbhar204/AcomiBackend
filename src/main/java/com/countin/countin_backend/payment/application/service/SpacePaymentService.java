package com.countin.countin_backend.payment.application.service;

import com.countin.countin_backend.common.exception.BusinessException;
import com.countin.countin_backend.occupancy.application.service.OccupancyTargetLabelBuilder;
import com.countin.countin_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.countin.countin_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.countin.countin_backend.payment.api.dto.request.ReviewSpacePaymentRequest;
import com.countin.countin_backend.payment.api.dto.request.SubmitSpacePaymentProofRequest;
import com.countin.countin_backend.payment.api.dto.response.PaymentTimelineEventResponse;
import com.countin.countin_backend.payment.api.dto.response.PaymentTimelineResponse;
import com.countin.countin_backend.payment.api.dto.response.SpacePaymentListResponse;
import com.countin.countin_backend.payment.api.dto.response.SpacePaymentResponse;
import com.countin.countin_backend.payment.domain.model.PaymentReviewAction;
import com.countin.countin_backend.payment.domain.model.PaymentTimelineEventType;
import com.countin.countin_backend.payment.domain.model.SpacePaymentCategory;
import com.countin.countin_backend.payment.domain.model.SpacePaymentMethod;
import com.countin.countin_backend.payment.domain.model.SpacePaymentStatus;
import com.countin.countin_backend.meal.application.support.MealPricingPolicy;
import com.countin.countin_backend.payment.domain.model.SpacePaymentType;
import com.countin.countin_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.countin.countin_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.countin.countin_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.countin.countin_backend.payment.infrastructure.persistence.entity.SpacePaymentTimelineEventEntity;
import com.countin.countin_backend.payment.infrastructure.persistence.repository.SpacePaymentRepository;
import com.countin.countin_backend.payment.infrastructure.persistence.repository.SpacePaymentTimelineEventRepository;
import com.countin.countin_backend.notification.application.service.PaymentNotificationSyncService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SpacePaymentService {

    private final SpacePaymentRepository paymentRepository;
    private final SpacePaymentTimelineEventRepository timelineEventRepository;
    private final SpacePaymentAccessService accessService;
    private final SpacePaymentGenerationService generationService;
    private final SpacePaymentTimelineService timelineService;
    private final SpaceRepository spaceRepository;
    private final OccupancyTargetLabelBuilder occupancyTargetLabelBuilder;
    private final PaymentNotificationSyncService paymentNotificationSyncService;

    @Transactional
    public SpacePaymentListResponse listPayments(
            UUID spaceId,
            UUID callerId,
            String monthParam,
            UUID memberIdParam,
            SpacePaymentStatus status,
            SpacePaymentType paymentType,
            SpacePaymentCategory paymentCategory) {
        return listPayments(
                spaceId, callerId, monthParam, memberIdParam, status, paymentType, paymentCategory, true);
    }

    @Transactional
    public SpacePaymentListResponse listPayments(
            UUID spaceId,
            UUID callerId,
            String monthParam,
            UUID memberIdParam,
            SpacePaymentStatus status,
            SpacePaymentType paymentType,
            SpacePaymentCategory paymentCategory,
            boolean syncExpected) {
        SpaceMembershipEntity membership = accessService.requireActiveMembership(spaceId, callerId);
        YearMonth month = parseMonth(monthParam);

        if (syncExpected) {
            generationService.syncExpectedPayments(spaceId, callerId, month);
        }

        UUID memberFilter = memberIdParam;
        if (!accessService.canViewAllPayments(membership)) {
            memberFilter = accessService.resolveOwnMemberId(spaceId, callerId);
        }

        List<SpacePaymentEntity> payments = paymentRepository.search(
                spaceId, month.toString(), memberFilter, status, paymentType, paymentCategory);

        SpaceEntity space = spaceRepository.findById(spaceId).orElse(null);
        if (space != null && !MealPricingPolicy.usesSeparateMealBilling(space)) {
            payments = payments.stream()
                    .filter(payment -> payment.getPaymentType() != SpacePaymentType.MEAL)
                    .toList();
        }

        return SpacePaymentListResponse.builder()
                .month(month.toString())
                .payments(payments.stream().map(this::toResponse).toList())
                .build();
    }

    @Transactional(readOnly = true)
    public SpacePaymentResponse getPayment(UUID spaceId, UUID paymentId, UUID callerId) {
        SpaceMembershipEntity membership = accessService.requireActiveMembership(spaceId, callerId);
        SpacePaymentEntity payment = loadPayment(spaceId, paymentId);
        accessService.requireViewPayment(membership, payment, callerId);
        return toResponse(payment);
    }

    @Transactional
    public SpacePaymentResponse submitProof(
            UUID spaceId, UUID paymentId, UUID callerId, SubmitSpacePaymentProofRequest request) {
        SpaceMembershipEntity membership = accessService.requireActiveMembership(spaceId, callerId);
        SpacePaymentEntity payment = loadPayment(spaceId, paymentId);
        accessService.requireSubmitProof(membership, payment, callerId);

        SpacePaymentStatus status = payment.getPaymentStatus();
        if (status == SpacePaymentStatus.PENDING
                || status == SpacePaymentStatus.REJECTED
                || status == SpacePaymentStatus.UPDATE_REQUESTED) {
            return submitNewProof(
                    payment,
                    callerId,
                    request,
                    status == SpacePaymentStatus.REJECTED || status == SpacePaymentStatus.UPDATE_REQUESTED);
        }
        if (status == SpacePaymentStatus.UNDER_REVIEW || status == SpacePaymentStatus.PROOF_UPLOADED) {
            return updatePendingProof(payment, request);
        }

        throw new BusinessException("Payment proof cannot be submitted in the current status", HttpStatus.BAD_REQUEST);
    }

    private SpacePaymentResponse submitNewProof(
            SpacePaymentEntity payment,
            UUID callerId,
            SubmitSpacePaymentProofRequest request,
            boolean resubmit) {
        validateProofImageIfPresent(request.getProofImageBase64());

        payment.setProofUrl(normalizeProofImage(request.getProofImageBase64()));
        payment.setReferenceNumber(trimToNull(request.getReferenceNumber()));
        payment.setRemarks(trimToNull(request.getRemarks()));
        if (request.getPaymentMethod() != null) {
            payment.setPaymentMethod(request.getPaymentMethod());
        }
        payment.setPaymentStatus(SpacePaymentStatus.UNDER_REVIEW);
        payment.setRejectionReason(null);
        payment.setRejectionCode(null);
        payment.setReviewedAt(null);
        payment.setReviewedBy(null);
        paymentRepository.save(payment);

        timelineService.record(
                payment,
                resubmit ? PaymentTimelineEventType.RESUBMITTED : PaymentTimelineEventType.PROOF_UPLOADED,
                request.getRemarks(),
                callerId);
        timelineService.record(payment, PaymentTimelineEventType.UNDER_REVIEW, null, callerId);
        paymentNotificationSyncService.onProofSubmitted(payment, callerId, resubmit);

        return toResponse(payment);
    }

    private SpacePaymentResponse updatePendingProof(
            SpacePaymentEntity payment, SubmitSpacePaymentProofRequest request) {
        validateProofImageIfPresent(request.getProofImageBase64());

        if (request.getProofImageBase64() != null && !request.getProofImageBase64().isBlank()) {
            payment.setProofUrl(normalizeProofImage(request.getProofImageBase64()));
        }
        payment.setReferenceNumber(trimToNull(request.getReferenceNumber()));
        payment.setRemarks(trimToNull(request.getRemarks()));
        if (request.getPaymentMethod() != null) {
            payment.setPaymentMethod(request.getPaymentMethod());
        }
        paymentRepository.save(payment);

        syncLatestProofEventRemarks(payment.getId(), payment.getRemarks());

        return toResponse(payment);
    }

    @Transactional
    public SpacePaymentResponse reviewPayment(
            UUID spaceId, UUID paymentId, UUID callerId, ReviewSpacePaymentRequest request) {
        accessService.requireManagePayments(spaceId, callerId);
        SpacePaymentEntity payment = loadPayment(spaceId, paymentId);

        if (payment.getPaymentStatus() != SpacePaymentStatus.UNDER_REVIEW
                && payment.getPaymentStatus() != SpacePaymentStatus.PROOF_UPLOADED
                && payment.getPaymentStatus() != SpacePaymentStatus.UPDATE_REQUESTED
                && payment.getPaymentStatus() != SpacePaymentStatus.REJECTED) {
            throw new BusinessException("Payment cannot be reviewed in the current status", HttpStatus.BAD_REQUEST);
        }

        if (request.getAction() == PaymentReviewAction.APPROVE) {
            payment.setPaymentStatus(SpacePaymentStatus.PAID);
            payment.setPaymentDate(LocalDate.now());
            payment.setReviewedAt(LocalDateTime.now());
            payment.setReviewedBy(callerId);
            payment.setRejectionReason(null);
            payment.setRejectionCode(null);
            if (request.getRemarks() != null && !request.getRemarks().isBlank()) {
                payment.setRemarks(request.getRemarks().trim());
            }
            paymentRepository.save(payment);
            timelineService.record(payment, PaymentTimelineEventType.APPROVED, request.getRemarks(), callerId);
            timelineService.record(payment, PaymentTimelineEventType.PAID, request.getRemarks(), callerId);
            paymentNotificationSyncService.onApproved(payment, callerId);
        } else if (request.getAction() == PaymentReviewAction.REQUEST_UPDATE) {
            if (request.getRemarks() == null || request.getRemarks().isBlank()) {
                throw new BusinessException("Update request message is required", HttpStatus.BAD_REQUEST);
            }
            payment.setPaymentStatus(SpacePaymentStatus.UPDATE_REQUESTED);
            payment.setReviewedAt(LocalDateTime.now());
            payment.setReviewedBy(callerId);
            payment.setRejectionCode(null);
            payment.setRejectionReason(trimToNull(request.getRemarks()));
            payment.setPaymentDate(null);
            paymentRepository.save(payment);
            timelineService.record(
                    payment, PaymentTimelineEventType.UPDATE_REQUESTED, request.getRemarks(), callerId);
            paymentNotificationSyncService.onUpdateRequested(payment, callerId, request.getRemarks());
        } else {
            if (request.getRejectionCode() == null) {
                throw new BusinessException("Rejection code is required", HttpStatus.BAD_REQUEST);
            }
            payment.setPaymentStatus(SpacePaymentStatus.REJECTED);
            payment.setReviewedAt(LocalDateTime.now());
            payment.setReviewedBy(callerId);
            payment.setRejectionCode(request.getRejectionCode());
            payment.setRejectionReason(trimToNull(request.getRemarks()));
            payment.setPaymentDate(null);
            paymentRepository.save(payment);
            timelineService.record(payment, PaymentTimelineEventType.REJECTED, request.getRemarks(), callerId);
            paymentNotificationSyncService.onRejected(payment, callerId, request.getRemarks());
        }

        return toResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentTimelineResponse getTimeline(UUID spaceId, UUID paymentId, UUID callerId) {
        SpaceMembershipEntity membership = accessService.requireActiveMembership(spaceId, callerId);
        SpacePaymentEntity payment = loadPayment(spaceId, paymentId);
        accessService.requireViewPayment(membership, payment, callerId);

        return PaymentTimelineResponse.builder()
                .paymentId(paymentId)
                .events(timelineEventRepository.findByPaymentIdOrderByPerformedAtAsc(paymentId).stream()
                        .map(PaymentTimelineEventResponse::from)
                        .toList())
                .build();
    }

    private SpacePaymentEntity loadPayment(UUID spaceId, UUID paymentId) {
        return paymentRepository
                .findByIdAndSpaceId(paymentId, spaceId)
                .orElseThrow(() -> new BusinessException("Payment not found", HttpStatus.NOT_FOUND));
    }

    private YearMonth parseMonth(String monthParam) {
        if (monthParam == null || monthParam.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(monthParam.trim());
        } catch (DateTimeParseException ex) {
            throw new BusinessException("Invalid month format. Use YYYY-MM", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateProofImageIfPresent(String proofImageBase64) {
        if (proofImageBase64 == null || proofImageBase64.isBlank()) {
            return;
        }
        String normalized = proofImageBase64.trim();
        if (normalized.length() < 32) {
            throw new BusinessException("Payment proof image is invalid", HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizeProofImage(String proofImageBase64) {
        return proofImageBase64 != null ? proofImageBase64.trim() : null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void syncLatestProofEventRemarks(UUID paymentId, String remarks) {
        List<SpacePaymentTimelineEventEntity> events =
                timelineEventRepository.findByPaymentIdOrderByPerformedAtAsc(paymentId);
        for (int index = events.size() - 1; index >= 0; index--) {
            PaymentTimelineEventType eventType = events.get(index).getEventType();
            if (eventType == PaymentTimelineEventType.PROOF_UPLOADED
                    || eventType == PaymentTimelineEventType.RESUBMITTED) {
                SpacePaymentTimelineEventEntity event = events.get(index);
                event.setRemarks(remarks);
                timelineEventRepository.save(event);
                return;
            }
        }
    }

    private SpacePaymentResponse toResponse(SpacePaymentEntity entity) {
        SpacePaymentResponse response = SpacePaymentResponse.from(entity);
        OccupancyEntity occupancy = entity.getOccupancy();
        if (occupancy == null) {
            return response;
        }
        String label = occupancyTargetLabelBuilder.build(occupancy);
        if (label == null || label.isBlank()) {
            return response;
        }
        return SpacePaymentResponse.builder()
                .paymentId(response.getPaymentId())
                .spaceId(response.getSpaceId())
                .memberId(response.getMemberId())
                .memberName(response.getMemberName())
                .occupancyId(response.getOccupancyId())
                .paymentType(response.getPaymentType())
                .paymentCategory(response.getPaymentCategory())
                .title(response.getTitle())
                .amount(response.getAmount())
                .currencyCode(response.getCurrencyCode())
                .dueDate(response.getDueDate())
                .month(response.getMonth())
                .paymentMethod(response.getPaymentMethod())
                .paymentStatus(response.getPaymentStatus())
                .proofUrl(response.getProofUrl())
                .referenceNumber(response.getReferenceNumber())
                .remarks(response.getRemarks())
                .rejectionReason(response.getRejectionReason())
                .rejectionCode(response.getRejectionCode())
                .reviewedBy(response.getReviewedBy())
                .reviewedAt(response.getReviewedAt())
                .paymentDate(response.getPaymentDate())
                .targetLabel(label)
                .createdAt(response.getCreatedAt())
                .updatedAt(response.getUpdatedAt())
                .build();
    }
}
