package com.amico.amico_backend.meal.api.controller;

import com.amico.amico_backend.common.security.SecurityUtils;
import com.amico.amico_backend.common.web.ApiResponse;
import com.amico.amico_backend.meal.api.dto.request.CreateMealComboRequest;
import com.amico.amico_backend.meal.api.dto.request.UpdateMealComboPriceRequest;
import com.amico.amico_backend.meal.api.dto.request.UpdateMealComboRequest;
import com.amico.amico_backend.meal.api.dto.response.MealComboResponse;
import com.amico.amico_backend.meal.application.service.MealComboService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spaces/{spaceId}/meal-combos")
@RequiredArgsConstructor
@Tag(name = "Meal Combos", description = "Space meal combo library")
@SecurityRequirement(name = "bearerAuth")
public class MealComboController {

    private final MealComboService mealComboService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<MealComboResponse>>> listCombos(@PathVariable UUID spaceId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Meal combos fetched successfully", mealComboService.listCombos(spaceId, callerId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MealComboResponse>> createCombo(
            @PathVariable UUID spaceId, @RequestBody @Valid CreateMealComboRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        MealComboResponse response = mealComboService.createCombo(spaceId, callerId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Meal combo created successfully", response));
    }

    @PutMapping("/{comboId}")
    public ResponseEntity<ApiResponse<MealComboResponse>> updateCombo(
            @PathVariable UUID spaceId,
            @PathVariable UUID comboId,
            @RequestBody @Valid UpdateMealComboRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Meal combo updated successfully",
                mealComboService.updateCombo(spaceId, comboId, callerId, request)));
    }

    @PutMapping("/{comboId}/price")
    public ResponseEntity<ApiResponse<MealComboResponse>> updateComboPrice(
            @PathVariable UUID spaceId,
            @PathVariable UUID comboId,
            @RequestBody @Valid UpdateMealComboPriceRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Meal combo price updated successfully",
                mealComboService.updateComboPrice(spaceId, comboId, callerId, request)));
    }

    @PostMapping("/{comboId}/deactivate")
    public ResponseEntity<Void> deactivateCombo(@PathVariable UUID spaceId, @PathVariable UUID comboId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        mealComboService.deactivateCombo(spaceId, comboId, callerId);
        return ResponseEntity.noContent().build();
    }
}
