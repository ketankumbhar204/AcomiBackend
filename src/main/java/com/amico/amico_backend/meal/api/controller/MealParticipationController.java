package com.amico.amico_backend.meal.api.controller;

import com.amico.amico_backend.common.security.SecurityUtils;
import com.amico.amico_backend.common.web.ApiResponse;
import com.amico.amico_backend.common.web.PagedResponse;
import com.amico.amico_backend.meal.api.dto.request.CreateMealParticipationRequest;
import com.amico.amico_backend.meal.api.dto.request.UpdateMealParticipationRequest;
import com.amico.amico_backend.meal.api.dto.response.MealParticipationDetailResponse;
import com.amico.amico_backend.meal.api.dto.response.MealParticipationResponse;
import com.amico.amico_backend.meal.api.dto.response.MemberMealActivityDayDetailResponse;
import com.amico.amico_backend.meal.api.dto.response.MemberMealActivityMonthResponse;
import com.amico.amico_backend.meal.api.dto.response.MealPollPaymentEventResponse;
import com.amico.amico_backend.meal.application.service.MealParticipationService;
import com.amico.amico_backend.meal.application.service.MealPollPaymentEventService;
import com.amico.amico_backend.meal.application.service.MemberMealActivityService;
import com.amico.amico_backend.meal.domain.model.MealParticipationStatus;
import com.amico.amico_backend.meal.domain.model.MealPlanCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/spaces/{spaceId}")
@RequiredArgsConstructor
@Tag(name = "Meal Participation", description = "Member meal enrollment")
@SecurityRequirement(name = "bearerAuth")
public class MealParticipationController {

    private final MealParticipationService mealParticipationService;
    private final MemberMealActivityService memberMealActivityService;
    private final MealPollPaymentEventService paymentEventService;

    @GetMapping("/meal-participations")
    @Operation(summary = "List meal participations")
    public ResponseEntity<ApiResponse<PagedResponse<MealParticipationResponse>>> listParticipations(
            @PathVariable UUID spaceId,
            @RequestParam(required = false) MealParticipationStatus status,
            @RequestParam(required = false) MealPlanCode mealPlanCode,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Meal participations fetched successfully",
                mealParticipationService.listParticipations(
                        spaceId, callerId, status, mealPlanCode, search, pageable)));
    }

    @GetMapping("/meal-participations/{participationId}")
    @Operation(summary = "Get meal participation detail")
    public ResponseEntity<ApiResponse<MealParticipationDetailResponse>> getParticipation(
            @PathVariable UUID spaceId, @PathVariable UUID participationId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Meal participation fetched successfully",
                mealParticipationService.getParticipation(spaceId, participationId, callerId)));
    }

    @PostMapping("/meal-participations")
    @Operation(summary = "Enroll member in meals")
    public ResponseEntity<ApiResponse<MealParticipationResponse>> enroll(
            @PathVariable UUID spaceId, @RequestBody @Valid CreateMealParticipationRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        MealParticipationResponse response = mealParticipationService.enroll(spaceId, callerId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Meal participation created successfully", response));
    }

    @PutMapping("/meal-participations/{participationId}")
    @Operation(summary = "Update meal participation")
    public ResponseEntity<ApiResponse<MealParticipationResponse>> updateParticipation(
            @PathVariable UUID spaceId,
            @PathVariable UUID participationId,
            @RequestBody @Valid UpdateMealParticipationRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Meal participation updated successfully",
                mealParticipationService.updateParticipation(spaceId, participationId, callerId, request)));
    }

    @PostMapping("/meal-participations/{participationId}/pause")
    public ResponseEntity<ApiResponse<MealParticipationResponse>> pause(
            @PathVariable UUID spaceId, @PathVariable UUID participationId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Meal participation paused",
                mealParticipationService.pause(spaceId, participationId, callerId)));
    }

    @PostMapping("/meal-participations/{participationId}/resume")
    public ResponseEntity<ApiResponse<MealParticipationResponse>> resume(
            @PathVariable UUID spaceId, @PathVariable UUID participationId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Meal participation resumed",
                mealParticipationService.resume(spaceId, participationId, callerId)));
    }

    @PostMapping("/meal-participations/{participationId}/stop")
    public ResponseEntity<ApiResponse<MealParticipationResponse>> stop(
            @PathVariable UUID spaceId, @PathVariable UUID participationId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Meal participation stopped",
                mealParticipationService.stop(spaceId, participationId, callerId)));
    }

    @GetMapping("/members/{memberId}/meal-participation")
    @Operation(summary = "Get member meal participation")
    public ResponseEntity<ApiResponse<MealParticipationDetailResponse>> getMemberParticipation(
            @PathVariable UUID spaceId, @PathVariable UUID memberId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Member meal participation fetched successfully",
                mealParticipationService.getMemberParticipation(spaceId, memberId, callerId)));
    }

    @GetMapping("/members/{memberId}/meal-activity/{date}")
    @Operation(summary = "Get member meal activity for a single day (path date)")
    public ResponseEntity<ApiResponse<MemberMealActivityDayDetailResponse>> getMemberMealActivityDayByPath(
            @PathVariable UUID spaceId,
            @PathVariable UUID memberId,
            @PathVariable String date) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Member meal activity day fetched successfully",
                memberMealActivityService.getDayDetail(spaceId, memberId, callerId, date)));
    }

    @GetMapping(value = "/members/{memberId}/meal-activity", params = "date")
    @Operation(summary = "Get member meal activity for a single day (date query param)")
    public ResponseEntity<ApiResponse<MemberMealActivityDayDetailResponse>> getMemberMealActivityDayByDate(
            @PathVariable UUID spaceId,
            @PathVariable UUID memberId,
            @RequestParam String date) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Member meal activity day fetched successfully",
                memberMealActivityService.getDayDetail(spaceId, memberId, callerId, date)));
    }

    @GetMapping("/members/{memberId}/meal-activity/day")
    @Operation(summary = "Get member meal activity for a single day")
    public ResponseEntity<ApiResponse<MemberMealActivityDayDetailResponse>> getMemberMealActivityDay(
            @PathVariable UUID spaceId,
            @PathVariable UUID memberId,
            @RequestParam String date) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Member meal activity day fetched successfully",
                memberMealActivityService.getDayDetail(spaceId, memberId, callerId, date)));
    }

    @GetMapping(value = "/members/{memberId}/meal-activity", params = "!date")
    @Operation(summary = "Get member monthly meal activity")
    public ResponseEntity<ApiResponse<MemberMealActivityMonthResponse>> getMemberMealActivity(
            @PathVariable UUID spaceId,
            @PathVariable UUID memberId,
            @RequestParam(required = false) String month) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Member meal activity fetched successfully",
                memberMealActivityService.getMonthlyActivity(spaceId, memberId, callerId, month)));
    }

    @GetMapping("/members/{memberId}/meal-payment-events")
    @Operation(summary = "Get member meal payment event timeline")
    public ResponseEntity<ApiResponse<List<MealPollPaymentEventResponse>>> getMemberMealPaymentEvents(
            @PathVariable UUID spaceId,
            @PathVariable UUID memberId,
            @RequestParam(required = false) String month) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Member meal payment events fetched successfully",
                paymentEventService.listForMemberMonth(spaceId, memberId, callerId, month)));
    }
}
