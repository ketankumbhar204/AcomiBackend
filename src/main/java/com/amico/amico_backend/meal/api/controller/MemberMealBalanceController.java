package com.amico.amico_backend.meal.api.controller;

import com.amico.amico_backend.common.security.SecurityUtils;
import com.amico.amico_backend.common.web.ApiResponse;
import com.amico.amico_backend.meal.api.dto.request.RecordMealBalancePurchaseRequest;
import com.amico.amico_backend.meal.api.dto.response.MemberMealBalanceActivityEventResponse;
import com.amico.amico_backend.meal.api.dto.response.MemberMealBalanceResponse;
import com.amico.amico_backend.meal.api.dto.response.MemberSubscriptionHistoryResponse;
import com.amico.amico_backend.meal.application.service.MemberMealBalanceService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.YearMonth;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spaces/{spaceId}/members/{memberId}/meal-balance")
@RequiredArgsConstructor
@Tag(name = "Member Meal Balance", description = "Prepaid meal balance wallet for members")
@SecurityRequirement(name = "bearerAuth")
public class MemberMealBalanceController {

    private final MemberMealBalanceService balanceService;

    @GetMapping
    public ResponseEntity<ApiResponse<MemberMealBalanceResponse>> getBalance(
            @PathVariable UUID spaceId, @PathVariable UUID memberId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Meal balance fetched successfully",
                balanceService.getBalance(spaceId, memberId, callerId)));
    }

    @GetMapping("/activity")
    public ResponseEntity<ApiResponse<List<MemberMealBalanceActivityEventResponse>>> getActivity(
            @PathVariable UUID spaceId,
            @PathVariable UUID memberId,
            @RequestParam String month) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        YearMonth yearMonth = YearMonth.parse(month);
        return ResponseEntity.ok(ApiResponse.success(
                "Meal balance activity fetched successfully",
                balanceService.getActivity(spaceId, memberId, callerId, yearMonth)));
    }

    @PostMapping("/purchases")
    public ResponseEntity<ApiResponse<MemberMealBalanceResponse>> recordPurchase(
            @PathVariable UUID spaceId,
            @PathVariable UUID memberId,
            @RequestBody @Valid RecordMealBalancePurchaseRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Meal balance purchase recorded successfully",
                        balanceService.recordPurchase(spaceId, memberId, callerId, request)));
    }

    @PostMapping("/end")
    public ResponseEntity<ApiResponse<MemberMealBalanceResponse>> endSubscription(
            @PathVariable UUID spaceId, @PathVariable UUID memberId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Subscription ended successfully",
                balanceService.endSubscription(spaceId, memberId, callerId)));
    }

    @GetMapping("/subscription-history")
    public ResponseEntity<ApiResponse<MemberSubscriptionHistoryResponse>> getSubscriptionHistory(
            @PathVariable UUID spaceId, @PathVariable UUID memberId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Subscription history fetched successfully",
                balanceService.getSubscriptionHistory(spaceId, memberId, callerId)));
    }
}
