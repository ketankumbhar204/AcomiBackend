package com.acomi.acomi_backend.dashboard.api.controller;

import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.dashboard.api.dto.response.MemberPaymentLedgerResponse;
import com.acomi.acomi_backend.dashboard.application.service.DashboardAccessService;
import com.acomi.acomi_backend.dashboard.application.service.SpaceBillingService;
import com.acomi.acomi_backend.payment.api.dto.request.ReviewSpacePaymentRequest;
import com.acomi.acomi_backend.payment.api.dto.request.SubmitSpacePaymentProofRequest;
import com.acomi.acomi_backend.payment.api.dto.response.OwnerPaymentsMonthResponse;
import com.acomi.acomi_backend.payment.api.dto.response.PaymentTimelineResponse;
import com.acomi.acomi_backend.payment.api.dto.response.PaymentsCardsPageResponse;
import com.acomi.acomi_backend.payment.api.dto.response.PaymentsMembersPageResponse;
import com.acomi.acomi_backend.payment.api.dto.response.PaymentsSummaryResponse;
import com.acomi.acomi_backend.payment.api.dto.response.SpacePaymentListResponse;
import com.acomi.acomi_backend.payment.api.dto.response.SpacePaymentResponse;
import com.acomi.acomi_backend.payment.application.service.OwnerPaymentsMonthService;
import com.acomi.acomi_backend.payment.application.service.PaymentsQueryService;
import com.acomi.acomi_backend.payment.application.service.SpacePaymentService;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentCategory;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
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
    private final OwnerPaymentsMonthService ownerPaymentsMonthService;
    private final PaymentsQueryService paymentsQueryService;

    @GetMapping
    @Operation(
            summary = "List space payments",
            description = "Read-only by default (sync=false). Pass sync=true only for explicit refresh.")
    public ResponseEntity<ApiResponse<SpacePaymentListResponse>> listPayments(
            @PathVariable UUID spaceId,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) UUID memberId,
            @RequestParam(required = false) SpacePaymentStatus status,
            @RequestParam(required = false) SpacePaymentType paymentType,
            @RequestParam(required = false) SpacePaymentCategory paymentCategory,
            @RequestParam(required = false, defaultValue = "false") boolean sync) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        SpacePaymentListResponse response = spacePaymentService.listPayments(
                spaceId, callerId, month, memberId, status, paymentType, paymentCategory, sync);
        return ResponseEntity.ok(ApiResponse.success("Payments fetched successfully", response));
    }

    @GetMapping("/summary")
    @Operation(
            summary = "Payments month summary",
            description = "Lightweight KPIs + tab counts. Never runs payment sync.")
    public ResponseEntity<ApiResponse<PaymentsSummaryResponse>> getSummary(
            @PathVariable UUID spaceId, @RequestParam(required = false) String month) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Payments summary fetched successfully",
                paymentsQueryService.getSummary(spaceId, callerId, month)));
    }

    @GetMapping("/members")
    @Operation(summary = "Paginated member payment ledger rows")
    public ResponseEntity<ApiResponse<PaymentsMembersPageResponse>> getMembers(
            @PathVariable UUID spaceId,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Payment members fetched successfully",
                paymentsQueryService.getMembers(spaceId, callerId, month, q, status, sort, page, size)));
    }

    @GetMapping("/review")
    @Operation(
            summary = "Paginated payment review queue",
            description = "queue=SUBMITTED|NEEDS_UPDATE|PENDING_REVIEW (default PENDING_REVIEW)")
    public ResponseEntity<ApiResponse<PaymentsCardsPageResponse>> getReview(
            @PathVariable UUID spaceId,
            @RequestParam(required = false) String month,
            @RequestParam(required = false, defaultValue = "PENDING_REVIEW") String queue,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Payment review queue fetched successfully",
                paymentsQueryService.getPaymentCards(spaceId, callerId, month, queue, page, size)));
    }

    @GetMapping("/history")
    @Operation(
            summary = "Paginated payment history",
            description = "queue defaults to HISTORY (PAID+REJECTED). Use PAID or REJECTED to narrow.")
    public ResponseEntity<ApiResponse<PaymentsCardsPageResponse>> getHistory(
            @PathVariable UUID spaceId,
            @RequestParam(required = false) String month,
            @RequestParam(required = false, defaultValue = "HISTORY") String queue,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Payment history fetched successfully",
                paymentsQueryService.getPaymentCards(spaceId, callerId, month, queue, page, size)));
    }

    @PostMapping("/sync")
    @Operation(
            summary = "Synchronize expected payments for a month",
            description = "Write command: generates expected rows and backfills meal-day proofs. "
                    + "Not used by default screen opens.")
    public ResponseEntity<ApiResponse<Map<String, String>>> syncMonth(
            @PathVariable UUID spaceId, @RequestParam(required = false) String month) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        ownerPaymentsMonthService.syncMonth(spaceId, callerId, month);
        return ResponseEntity.ok(ApiResponse.success(
                "Payments synchronized successfully", Map.of("month", month != null ? month : "")));
    }

    @GetMapping("/ledger")
    @Operation(
            summary = "Get member payment ledger",
            description = "Deprecated for UI screens — prefer /payments/summary and /payments/members.")
    public ResponseEntity<ApiResponse<MemberPaymentLedgerResponse>> getLedger(
            @PathVariable UUID spaceId,
            @RequestParam(required = false) String month) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        dashboardAccessService.requireManagePayments(spaceId, callerId);
        MemberPaymentLedgerResponse response = spaceBillingService.buildLedger(spaceId, callerId, month);
        return ResponseEntity.ok(ApiResponse.success("Payment ledger fetched successfully", response));
    }

    @GetMapping("/owner-month")
    @Operation(
            summary = "Owner Payments month aggregate (legacy)",
            description = "Deprecated. Prefer /summary + /members + /review. "
                    + "Read-only by default; pass sync=true to run sync commands first.")
    public ResponseEntity<ApiResponse<OwnerPaymentsMonthResponse>> getOwnerMonth(
            @PathVariable UUID spaceId,
            @RequestParam(required = false) String month,
            @RequestParam(required = false, defaultValue = "false") boolean sync) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        if (sync) {
            ownerPaymentsMonthService.syncMonth(spaceId, callerId, month);
        }
        OwnerPaymentsMonthResponse response =
                ownerPaymentsMonthService.buildOwnerMonth(spaceId, callerId, month);
        return ResponseEntity.ok(ApiResponse.success("Owner payments month fetched successfully", response));
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
