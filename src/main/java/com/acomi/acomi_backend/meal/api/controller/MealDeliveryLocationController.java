package com.acomi.acomi_backend.meal.api.controller;

import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.meal.api.dto.request.CreateMealDeliveryLocationRequest;
import com.acomi.acomi_backend.meal.api.dto.request.ReorderMealDeliveryLocationsRequest;
import com.acomi.acomi_backend.meal.api.dto.request.UpdateMealDeliveryLocationRequest;
import com.acomi.acomi_backend.meal.api.dto.response.MealDeliveryLocationResponse;
import com.acomi.acomi_backend.meal.application.service.MealDeliveryLocationService;
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
@RequestMapping("/api/v1/spaces/{spaceId}/meal-delivery-locations")
@RequiredArgsConstructor
@Tag(name = "Meal Delivery Locations", description = "Mess delivery drop points for meal polls")
@SecurityRequirement(name = "bearerAuth")
public class MealDeliveryLocationController {

    private final MealDeliveryLocationService deliveryLocationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<MealDeliveryLocationResponse>>> listActive(@PathVariable UUID spaceId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Delivery locations fetched successfully",
                deliveryLocationService.listActive(spaceId, callerId)));
    }

    @GetMapping("/manage")
    public ResponseEntity<ApiResponse<List<MealDeliveryLocationResponse>>> listAll(@PathVariable UUID spaceId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Delivery locations fetched successfully",
                deliveryLocationService.listAll(spaceId, callerId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MealDeliveryLocationResponse>> create(
            @PathVariable UUID spaceId, @RequestBody @Valid CreateMealDeliveryLocationRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Delivery location created successfully",
                        deliveryLocationService.create(spaceId, callerId, request)));
    }

    @PostMapping("/reorder")
    public ResponseEntity<ApiResponse<List<MealDeliveryLocationResponse>>> reorder(
            @PathVariable UUID spaceId, @RequestBody @Valid ReorderMealDeliveryLocationsRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Delivery locations reordered successfully",
                deliveryLocationService.reorder(spaceId, callerId, request)));
    }

    @PutMapping("/{locationId}")
    public ResponseEntity<ApiResponse<MealDeliveryLocationResponse>> update(
            @PathVariable UUID spaceId,
            @PathVariable UUID locationId,
            @RequestBody @Valid UpdateMealDeliveryLocationRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Delivery location updated successfully",
                deliveryLocationService.update(spaceId, locationId, callerId, request)));
    }
}
