package com.countin.countin_backend.meal.api.controller;

import com.countin.countin_backend.common.security.SecurityUtils;
import com.countin.countin_backend.common.web.ApiResponse;
import com.countin.countin_backend.meal.api.dto.request.ApproveMealPollPaymentRequest;
import com.countin.countin_backend.meal.api.dto.request.RejectMealPollPaymentRequest;
import com.countin.countin_backend.meal.api.dto.request.SubmitBulkMealPollPaymentProofRequest;
import com.countin.countin_backend.meal.api.dto.request.SubmitMealPollPaymentProofRequest;
import com.countin.countin_backend.meal.api.dto.request.SubmitMealPollResponsesRequest;
import com.countin.countin_backend.meal.api.dto.request.UpdateMealPollCloseAtRequest;
import com.countin.countin_backend.meal.api.dto.response.BulkMealPollPaymentProofResponse;
import com.countin.countin_backend.meal.api.dto.response.MealPollDayResponse;
import com.countin.countin_backend.meal.api.dto.response.MealPollResponse;
import com.countin.countin_backend.meal.application.service.MealPollService;
import com.countin.countin_backend.meal.domain.model.MealType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/v1/spaces/{spaceId}/meal-polls")
@RequiredArgsConstructor
@Tag(name = "Meal Polls", description = "Menu option polls and member responses (Phase 6)")
@SecurityRequirement(name = "bearerAuth")
public class MealPollController {

    private final MealPollService mealPollService;

    @GetMapping
    public ResponseEntity<ApiResponse<MealPollDayResponse>> listPolls(
            @PathVariable UUID spaceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Meal polls fetched successfully", mealPollService.getPollsForDate(spaceId, callerId, date)));
    }

    @GetMapping("/{date}/{mealType}")
    public ResponseEntity<ApiResponse<MealPollResponse>> getPoll(
            @PathVariable UUID spaceId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PathVariable MealType mealType) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Meal poll fetched successfully", mealPollService.getPoll(spaceId, callerId, date, mealType)));
    }

    @PostMapping("/{date}/{mealType}/open")
    public ResponseEntity<ApiResponse<MealPollResponse>> openPoll(
            @PathVariable UUID spaceId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PathVariable MealType mealType) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Meal poll opened successfully", mealPollService.openPoll(spaceId, callerId, date, mealType)));
    }

    @PostMapping("/{date}/{mealType}/close")
    public ResponseEntity<ApiResponse<MealPollResponse>> closePoll(
            @PathVariable UUID spaceId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PathVariable MealType mealType) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Meal poll closed successfully", mealPollService.closePoll(spaceId, callerId, date, mealType)));
    }

    @PutMapping("/{date}/{mealType}/close-at")
    public ResponseEntity<ApiResponse<MealPollResponse>> updateCloseAt(
            @PathVariable UUID spaceId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PathVariable MealType mealType,
            @RequestBody @Valid UpdateMealPollCloseAtRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Poll closing time updated",
                mealPollService.updatePollCloseAt(
                        spaceId, callerId, date, mealType, request.getPollCloseAt())));
    }

    @PostMapping("/{date}/responses")
    public ResponseEntity<ApiResponse<MealPollDayResponse>> submitResponses(
            @PathVariable UUID spaceId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestBody @Valid SubmitMealPollResponsesRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Meal poll responses saved successfully",
                mealPollService.submitResponses(spaceId, callerId, date, request)));
    }

    @PostMapping("/{date}/payment-proof")
    public ResponseEntity<ApiResponse<MealPollDayResponse>> submitPaymentProof(
            @PathVariable UUID spaceId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestBody @Valid SubmitMealPollPaymentProofRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Payment proof submitted successfully",
                mealPollService.submitPaymentProof(spaceId, callerId, date, request)));
    }

    @PostMapping("/payment-proof/bulk")
    public ResponseEntity<ApiResponse<BulkMealPollPaymentProofResponse>> submitBulkPaymentProof(
            @PathVariable UUID spaceId,
            @RequestBody @Valid SubmitBulkMealPollPaymentProofRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Bulk payment proof submitted successfully",
                mealPollService.submitBulkPaymentProof(spaceId, callerId, request)));
    }

    @PostMapping("/{date}/payments/{memberId}/approve")
    public ResponseEntity<ApiResponse<MealPollDayResponse>> approvePayment(
            @PathVariable UUID spaceId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PathVariable UUID memberId,
            @RequestBody(required = false) ApproveMealPollPaymentRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Payment approved successfully",
                mealPollService.approvePayment(spaceId, callerId, date, memberId, request)));
    }

    @PostMapping("/{date}/payments/{memberId}/reject")
    public ResponseEntity<ApiResponse<MealPollDayResponse>> rejectPayment(
            @PathVariable UUID spaceId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PathVariable UUID memberId,
            @RequestBody(required = false) RejectMealPollPaymentRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Payment proof rejected",
                mealPollService.rejectPayment(spaceId, callerId, date, memberId, request)));
    }

    @PostMapping("/{date}/payments/{memberId}/remind")
    public ResponseEntity<ApiResponse<MealPollDayResponse>> sendPaymentReminder(
            @PathVariable UUID spaceId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PathVariable UUID memberId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Payment reminder recorded",
                mealPollService.sendPaymentReminder(spaceId, callerId, date, memberId)));
    }
}
