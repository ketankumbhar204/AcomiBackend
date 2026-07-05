package com.countin.countin_backend.payment.application.service;

import com.countin.countin_backend.common.exception.BusinessException;
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
import com.countin.countin_backend.payment.domain.model.SpacePaymentType;
import com.countin.countin_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.countin.countin_backend.payment.infrastructure.persistence.repository.SpacePaymentRepository;
import com.countin.countin_backend.payment.infrastructure.persistence.repository.SpacePaymentTimelineEventRepository;
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

    @Transactional
    public SpacePaymentListResponse listPayments(
            UUID spaceId,
            UUID callerId,
            String monthParam,
            UUID memberIdParam,
            SpacePaymentStatus status,
            SpacePaymentType paymentType,
            SpacePaymentCategory paymentCategory) {
        SpaceMembershipEntity membership = accessService.requireActiveMembership(spaceId, callerId);
        YearMonth month = parseMonth(monthParam);

        generationService.syncExpectedPayments(spaceId, callerId, month);

        UUID memberFilter = memberIdParam;
        if (!accessService.canViewAllPayments(membership)) {
            memberFilter = accessService.resolveOwnMemberId(spaceId, callerId);
        }

        List<SpacePaymentEntity> payments = paymentRepository.search(
                spaceId, month.toString(), memberFilter, status, paymentType, paymentCategory);

        return SpacePaymentListResponse.builder()
                .month(month.toString())
                .payments(payments.stream().map(SpacePaymentResponse::from).toList())
                .build();
    }

    @Transactional(readOnly = true)
    public SpacePaymentResponse getPayment(UUID spaceId, UUID paymentId, UUID callerId) {
        SpaceMembershipEntity membership = accessService.requireActiveMembership(spaceId, callerId);
        SpacePaymentEntity payment = loadPayment(spaceId, paymentId);
        accessService.requireViewPayment(membership, payment, callerId);
        return SpacePaymentResponse.from(payment);
    }

    @Transactional
    public SpacePaymentResponse submitProof(
            UUID spaceId, UUID paymentId, UUID callerId, SubmitSpacePaymentProofRequest request) {
        SpaceMembershipEntity membership = accessService.requireActiveMembership(spaceId, callerId);
        SpacePaymentEntity payment = loadPayment(spaceId, paymentId);
        accessService.requireSubmitProof(membership, payment, callerId);

        if (payment.getPaymentStatus() != SpacePaymentStatus.PENDING
                && payment.getPaymentStatus() != SpacePaymentStatus.REJECTED) {
            throw new BusinessException("Payment proof cannot be submitted in the current status", HttpStatus.BAD_REQUEST);
        }

        boolean resubmit = payment.getPaymentStatus() == SpacePaymentStatus.REJECTED;
        validateProofImage(request.getProofImageBase64());

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

        return SpacePaymentResponse.from(payment);
    }

    @Transactional
    public SpacePaymentResponse reviewPayment(
            UUID spaceId, UUID paymentId, UUID callerId, ReviewSpacePaymentRequest request) {
        accessService.requireManagePayments(spaceId, callerId);
        SpacePaymentEntity payment = loadPayment(spaceId, paymentId);

        if (payment.getPaymentStatus() != SpacePaymentStatus.UNDER_REVIEW
                && payment.getPaymentStatus() != SpacePaymentStatus.PROOF_UPLOADED) {
            throw new BusinessException("No payment proof is awaiting review", HttpStatus.BAD_REQUEST);
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
        }

        return SpacePaymentResponse.from(payment);
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

    private void validateProofImage(String proofImageBase64) {
        if (proofImageBase64 == null || proofImageBase64.isBlank()) {
            throw new BusinessException("Payment proof image is required", HttpStatus.BAD_REQUEST);
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
}
