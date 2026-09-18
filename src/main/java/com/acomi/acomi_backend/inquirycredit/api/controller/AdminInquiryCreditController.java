package com.acomi.acomi_backend.inquirycredit.api.controller;

import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.common.web.PagedResponse;
import com.acomi.acomi_backend.inquirycredit.api.dto.request.RejectPurchaseRequest;
import com.acomi.acomi_backend.inquirycredit.api.dto.request.UpdateCreditPackageRequest;
import com.acomi.acomi_backend.inquirycredit.api.dto.request.UpdatePaymentConfigRequest;
import com.acomi.acomi_backend.inquirycredit.api.dto.response.InquiryAdminSummaryResponse;
import com.acomi.acomi_backend.inquirycredit.api.dto.response.InquiryCreditPackageResponse;
import com.acomi.acomi_backend.inquirycredit.api.dto.response.InquiryCreditPurchaseRequestResponse;
import com.acomi.acomi_backend.inquirycredit.api.dto.response.InquiryPaymentConfigResponse;
import com.acomi.acomi_backend.inquirycredit.application.service.InquiryCreditPackageAdminService;
import com.acomi.acomi_backend.inquirycredit.application.service.InquiryCreditPurchaseService;
import com.acomi.acomi_backend.inquirycredit.application.service.InquiryPaymentConfigService;
import com.acomi.acomi_backend.inquirycredit.domain.model.InquiryCreditPurchaseStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/inquiry-credits")
@RequiredArgsConstructor
@Tag(name = "Admin Inquiry Credits", description = "Admin management for inquiry credit packages, config, and purchase requests")
@SecurityRequirement(name = "bearerAuth")
public class AdminInquiryCreditController {

    private final InquiryPaymentConfigService paymentConfigService;
    private final InquiryCreditPurchaseService purchaseService;
    private final InquiryCreditPackageAdminService packageAdminService;

    // ── Payment config ─────────────────────────────────────────────────────────

    @GetMapping("/payment-config")
    @Operation(summary = "Get inquiry payment configuration")
    public ResponseEntity<ApiResponse<InquiryPaymentConfigResponse>> getConfig() {
        UUID adminId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(paymentConfigService.adminGet(adminId)));
    }

    @PutMapping("/payment-config")
    @Operation(summary = "Update inquiry payment configuration")
    public ResponseEntity<ApiResponse<InquiryPaymentConfigResponse>> updateConfig(
            @RequestBody @Valid UpdatePaymentConfigRequest request) {
        UUID adminId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(
                ApiResponse.success("Config updated", paymentConfigService.adminUpdate(request, adminId)));
    }

    // ── Credit packages ────────────────────────────────────────────────────────

    @GetMapping("/packages")
    @Operation(summary = "List all inquiry credit packages")
    public ResponseEntity<ApiResponse<List<InquiryCreditPackageResponse>>> listPackages() {
        return ResponseEntity.ok(ApiResponse.success(packageAdminService.listAll()));
    }

    @PutMapping("/packages/{id}")
    @Operation(summary = "Update an inquiry credit package")
    public ResponseEntity<ApiResponse<InquiryCreditPackageResponse>> updatePackage(
            @PathVariable UUID id, @RequestBody UpdateCreditPackageRequest request) {
        UUID adminId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(
                ApiResponse.success("Package updated", packageAdminService.update(id, request, adminId)));
    }

    // ── Purchase requests ──────────────────────────────────────────────────────

    @GetMapping("/purchase-requests")
    @Operation(summary = "List all inquiry credit purchase requests")
    public ResponseEntity<ApiResponse<PagedResponse<InquiryCreditPurchaseRequestResponse>>> listRequests(
            @RequestParam(required = false) InquiryCreditPurchaseStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<InquiryCreditPurchaseRequestResponse> page = purchaseService.listForAdmin(status, pageable);
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(page)));
    }

    @PostMapping("/purchase-requests/{id}/approve")
    @Operation(summary = "Approve a pending inquiry credit purchase request")
    public ResponseEntity<ApiResponse<InquiryCreditPurchaseRequestResponse>> approve(@PathVariable UUID id) {
        UUID adminId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(
                ApiResponse.success("Purchase request approved", purchaseService.approve(id, adminId)));
    }

    @PostMapping("/purchase-requests/{id}/reject")
    @Operation(summary = "Reject a pending inquiry credit purchase request")
    public ResponseEntity<ApiResponse<InquiryCreditPurchaseRequestResponse>> reject(
            @PathVariable UUID id,
            @RequestBody(required = false) RejectPurchaseRequest request) {
        UUID adminId = SecurityUtils.getCurrentUserId();
        String reason = request != null ? request.getReason() : null;
        return ResponseEntity.ok(
                ApiResponse.success("Purchase request rejected", purchaseService.reject(id, adminId, reason)));
    }

    @GetMapping("/summary")
    @Operation(summary = "Get inquiry credit admin summary (pending count etc.)")
    public ResponseEntity<ApiResponse<InquiryAdminSummaryResponse>> summary() {
        return ResponseEntity.ok(ApiResponse.success(purchaseService.adminSummary()));
    }
}
