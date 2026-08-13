package com.acomi.acomi_backend.meal.api.controller;

import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.meal.api.dto.response.EligibleParticipantResponse;
import com.acomi.acomi_backend.meal.api.dto.response.MealEligibilitySummaryResponse;
import com.acomi.acomi_backend.meal.api.dto.response.MealHeadcountDayResponse;
import com.acomi.acomi_backend.meal.api.dto.response.MealHeadcountDetailResponse;
import com.acomi.acomi_backend.meal.api.dto.response.MealSharePreviewResponse;
import com.acomi.acomi_backend.meal.application.service.MealEligibilityService;
import com.acomi.acomi_backend.meal.application.service.MealHeadcountService;
import com.acomi.acomi_backend.meal.application.service.MealSharePreviewService;
import com.acomi.acomi_backend.meal.domain.model.MealType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spaces/{spaceId}/meals")
@RequiredArgsConstructor
@Tag(name = "Meal Eligibility", description = "Poll audience eligibility (Phase 5)")
@SecurityRequirement(name = "bearerAuth")
public class MealEligibilityController {

    private final MealEligibilityService mealEligibilityService;
    private final MealHeadcountService mealHeadcountService;
    private final MealSharePreviewService mealSharePreviewService;

    @GetMapping("/eligibility-summary")
    public ResponseEntity<ApiResponse<MealEligibilitySummaryResponse>> getSummary(
            @org.springframework.web.bind.annotation.PathVariable UUID spaceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Meal eligibility summary fetched successfully",
                mealEligibilityService.getSummary(spaceId, callerId, date)));
    }

    @GetMapping("/eligible-participants")
    public ResponseEntity<ApiResponse<List<EligibleParticipantResponse>>> listEligibleParticipants(
            @org.springframework.web.bind.annotation.PathVariable UUID spaceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam MealType mealType) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Eligible participants fetched successfully",
                mealEligibilityService.listEligibleParticipants(spaceId, callerId, date, mealType)));
    }

    @GetMapping("/share-preview")
    public ResponseEntity<ApiResponse<MealSharePreviewResponse>> getSharePreview(
            @org.springframework.web.bind.annotation.PathVariable UUID spaceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) MealType mealType) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Meal share preview fetched successfully",
                mealSharePreviewService.getSharePreview(spaceId, callerId, date, mealType)));
    }

    @GetMapping("/headcount")
    public ResponseEntity<ApiResponse<?>> getHeadcount(
            @org.springframework.web.bind.annotation.PathVariable UUID spaceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) MealType mealType) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        if (mealType != null) {
            return ResponseEntity.ok(ApiResponse.success(
                    "Meal headcount detail fetched successfully",
                    mealHeadcountService.getMealDetail(spaceId, callerId, date, mealType)));
        }
        return ResponseEntity.ok(ApiResponse.success(
                "Meal headcount summary fetched successfully",
                mealHeadcountService.getDaySummary(spaceId, callerId, date)));
    }
}
