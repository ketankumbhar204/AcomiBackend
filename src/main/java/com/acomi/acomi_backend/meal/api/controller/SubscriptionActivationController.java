package com.acomi.acomi_backend.meal.api.controller;

import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.meal.api.dto.request.CreateSubscriptionActivationRequest;
import com.acomi.acomi_backend.meal.api.dto.request.ResolveSubscriptionActivationRequest;
import com.acomi.acomi_backend.meal.api.dto.response.CustomerSubscriptionStatusResponse;
import com.acomi.acomi_backend.meal.api.dto.response.SubscriptionActivationRequestResponse;
import com.acomi.acomi_backend.meal.application.service.SubscriptionActivationRequestService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Subscription Activation", description = "Customer subscription activation requests")
@SecurityRequirement(name = "bearerAuth")
public class SubscriptionActivationController {

    private final SubscriptionActivationRequestService activationRequestService;

    @GetMapping("/spaces/{spaceId}/subscription-activation-requests/pending")
    public ResponseEntity<ApiResponse<List<SubscriptionActivationRequestResponse>>> listPending(
            @PathVariable UUID spaceId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Pending subscription requests fetched successfully",
                activationRequestService.listPendingForSpace(spaceId, callerId)));
    }

    @GetMapping("/spaces/{spaceId}/members/{memberId}/subscription-activation-requests")
    public ResponseEntity<ApiResponse<List<SubscriptionActivationRequestResponse>>> listForMember(
            @PathVariable UUID spaceId, @PathVariable UUID memberId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Subscription requests fetched successfully",
                activationRequestService.listForMember(spaceId, memberId, callerId)));
    }

    @PostMapping("/spaces/{spaceId}/members/{memberId}/subscription-activation-requests")
    public ResponseEntity<ApiResponse<SubscriptionActivationRequestResponse>> create(
            @PathVariable UUID spaceId,
            @PathVariable UUID memberId,
            @RequestBody @Valid CreateSubscriptionActivationRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        SubscriptionActivationRequestResponse response =
                activationRequestService.createRequest(spaceId, memberId, callerId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Subscription activation request submitted", response));
    }

    @PostMapping("/spaces/{spaceId}/subscription-activation-requests/{requestId}/approve")
    public ResponseEntity<ApiResponse<SubscriptionActivationRequestResponse>> approve(
            @PathVariable UUID spaceId,
            @PathVariable UUID requestId,
            @RequestBody(required = false) ResolveSubscriptionActivationRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Subscription activation approved",
                activationRequestService.approve(spaceId, requestId, callerId, request)));
    }

    @PostMapping("/spaces/{spaceId}/subscription-activation-requests/{requestId}/reject")
    public ResponseEntity<ApiResponse<SubscriptionActivationRequestResponse>> reject(
            @PathVariable UUID spaceId,
            @PathVariable UUID requestId,
            @RequestBody(required = false) ResolveSubscriptionActivationRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Subscription activation rejected",
                activationRequestService.reject(spaceId, requestId, callerId, request)));
    }

    @GetMapping("/spaces/{spaceId}/members/me/subscription-status")
    public ResponseEntity<ApiResponse<CustomerSubscriptionStatusResponse>> myCustomerStatus(
            @PathVariable UUID spaceId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Subscription status fetched successfully",
                activationRequestService.getMyCustomerStatus(spaceId, callerId)));
    }

    @GetMapping("/spaces/{spaceId}/members/{memberId}/subscription-status")
    public ResponseEntity<ApiResponse<CustomerSubscriptionStatusResponse>> customerStatus(
            @PathVariable UUID spaceId, @PathVariable UUID memberId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Subscription status fetched successfully",
                activationRequestService.getCustomerStatus(spaceId, memberId, callerId)));
    }
}
