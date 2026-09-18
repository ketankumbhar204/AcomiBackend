package com.acomi.acomi_backend.inquirycredit.api.controller;

import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.inquirycredit.api.dto.request.CreateInquiryPurchaseRequest;
import com.acomi.acomi_backend.inquirycredit.api.dto.response.InquiryCreditPurchaseRequestResponse;
import com.acomi.acomi_backend.inquirycredit.api.dto.response.InquiryPaymentConfigResponse;
import com.acomi.acomi_backend.inquirycredit.api.dto.response.InquiryWalletResponse;
import com.acomi.acomi_backend.inquirycredit.application.service.InquiryCreditPurchaseService;
import com.acomi.acomi_backend.inquirycredit.application.service.InquiryCreditWalletService;
import com.acomi.acomi_backend.inquirycredit.application.service.InquiryPaymentConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inquiry-credits")
@RequiredArgsConstructor
@Tag(name = "Inquiry Credits", description = "Inquiry credit wallet and purchase requests for seekers")
@SecurityRequirement(name = "bearerAuth")
public class InquiryCreditController {

    private final InquiryCreditWalletService walletService;
    private final InquiryPaymentConfigService paymentConfigService;
    private final InquiryCreditPurchaseService purchaseService;

    @GetMapping("/wallet")
    @Operation(summary = "Get my inquiry credit wallet balance")
    public ResponseEntity<ApiResponse<InquiryWalletResponse>> getWallet() {
        UUID callerId = SecurityUtils.getCurrentUserId();
        InquiryWalletResponse wallet = InquiryWalletResponse.from(walletService.getOrCreateWallet(callerId));
        return ResponseEntity.ok(ApiResponse.success(wallet));
    }

    @GetMapping("/payment-config")
    @Operation(summary = "Get inquiry payment configuration and available packages")
    public ResponseEntity<ApiResponse<InquiryPaymentConfigResponse>> getPaymentConfig() {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(paymentConfigService.getPublicConfig(callerId)));
    }

    @PostMapping("/purchase-requests")
    @Operation(summary = "Submit a payment request to purchase inquiry credits")
    public ResponseEntity<ApiResponse<InquiryCreditPurchaseRequestResponse>> createPurchaseRequest(
            @RequestBody @Valid CreateInquiryPurchaseRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        InquiryCreditPurchaseRequestResponse response =
                purchaseService.createRequest(callerId, request.getPackageId(), request.getUtr());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Purchase request submitted", response));
    }

    @GetMapping("/purchase-requests/me")
    @Operation(summary = "List my inquiry credit purchase requests")
    public ResponseEntity<ApiResponse<List<InquiryCreditPurchaseRequestResponse>>> listMyPurchaseRequests() {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(purchaseService.listMine(callerId)));
    }
}
