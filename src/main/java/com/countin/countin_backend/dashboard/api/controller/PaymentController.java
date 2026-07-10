package com.countin.countin_backend.dashboard.api.controller;

import com.countin.countin_backend.common.security.SecurityUtils;
import com.countin.countin_backend.common.web.ApiResponse;
import com.countin.countin_backend.dashboard.api.dto.response.MemberPaymentLedgerResponse;
import com.countin.countin_backend.dashboard.application.service.DashboardAccessService;
import com.countin.countin_backend.dashboard.application.service.SpaceBillingService;
import com.countin.countin_backend.payment.api.dto.request.ReviewSpacePaymentRequest;
import com.countin.countin_backend.payment.api.dto.request.SubmitSpacePaymentProofRequest;
import com.countin.countin_backend.payment.api.dto.response.PaymentTimelineResponse;
import com.countin.countin_backend.payment.api.dto.response.SpacePaymentListResponse;
import com.countin.countin_backend.payment.api.dto.response.SpacePaymentResponse;
import com.countin.countin_backend.payment.application.service.SpacePaymentService;
import com.countin.countin_backend.payment.domain.model.SpacePaymentCategory;
import com.countin.countin_backend.payment.domain.model.SpacePaymentStatus;
import com.countin.countin_backend.payment.domain.model.SpacePaymentType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spaces/{spaceId}/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Space payment ledger APIs")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final DashboardAccessService dashboardAccessService;
    private final SpaceBillingService spaceBillingService;
    private final SpacePaymentService spacePaymentService;

    @GetMapping
    @Operation(summary = "List space payments", description = "Returns universal payment records for the selected month.")
    public ResponseEntity<ApiResponse<SpacePaymentListResponse>> listPayments(
            @PathVariable UUID spaceId,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) UUID memberId,
            @RequestParam(required = false) SpacePaymentStatus status,
            @RequestParam(required = false) SpacePaymentType paymentType,
            @RequestParam(required = false) SpacePaymentCategory paymentCategory,
            @RequestParam(required = false, defaultValue = "true") boolean sync) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        SpacePaymentListResponse response = spacePaymentService.listPayments(
                spaceId, callerId, month, memberId, status, paymentType, paymentCategory, sync);
        return ResponseEntity.ok(ApiResponse.success("Payments fetched successfully", response));
    }

    @GetMapping("/ledger")
    @Operation(
            summary = "Get member payment ledger",
            description = "Returns per-member expected charges, collected amounts, and pending balances "
                    + "for the selected month.")
    public ResponseEntity<ApiResponse<MemberPaymentLedgerResponse>> getLedger(
            @PathVariable UUID spaceId,
            @RequestParam(required = false) String month) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        dashboardAccessService.requireManagePayments(spaceId, callerId);
        MemberPaymentLedgerResponse response = spaceBillingService.buildLedger(spaceId, callerId, month);
        return ResponseEntity.ok(ApiResponse.success("Payment ledger fetched successfully", response));
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "Get payment details")
    public ResponseEntity<ApiResponse<SpacePaymentResponse>> getPayment(
            @PathVariable UUID spaceId, @PathVariable UUID paymentId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Payment fetched successfully", spacePaymentService.getPayment(spaceId, paymentId, callerId)));
    }

    @PostMapping("/{paymentId}/proof")
    @Operation(summary = "Submit payment proof")
    public ResponseEntity<ApiResponse<SpacePaymentResponse>> submitProof(
            @PathVariable UUID spaceId,
            @PathVariable UUID paymentId,
            @RequestBody @Valid SubmitSpacePaymentProofRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Payment proof submitted successfully",
                spacePaymentService.submitProof(spaceId, paymentId, callerId, request)));
    }

    @PostMapping("/{paymentId}/review")
    @Operation(summary = "Approve or reject payment proof")
    public ResponseEntity<ApiResponse<SpacePaymentResponse>> reviewPayment(
            @PathVariable UUID spaceId,
            @PathVariable UUID paymentId,
            @RequestBody @Valid ReviewSpacePaymentRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Payment reviewed successfully",
                spacePaymentService.reviewPayment(spaceId, paymentId, callerId, request)));
    }

    @GetMapping("/{paymentId}/timeline")
    @Operation(summary = "Get payment timeline")
    public ResponseEntity<ApiResponse<PaymentTimelineResponse>> getTimeline(
            @PathVariable UUID spaceId, @PathVariable UUID paymentId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Payment timeline fetched successfully",
                spacePaymentService.getTimeline(spaceId, paymentId, callerId)));
    }
}
