package com.countin.countin_backend.meal.api.controller;

import com.countin.countin_backend.common.security.SecurityUtils;
import com.countin.countin_backend.common.web.ApiResponse;
import com.countin.countin_backend.meal.api.dto.request.CreateFoodCategoryRequest;
import com.countin.countin_backend.meal.api.dto.request.CreateFoodItemRequest;
import com.countin.countin_backend.meal.api.dto.request.UpdateFoodItemDefaultPriceRequest;
import com.countin.countin_backend.meal.api.dto.request.UpdateFoodItemExtraRequest;
import com.countin.countin_backend.meal.api.dto.request.UpdateFoodItemRequest;
import com.countin.countin_backend.meal.api.dto.response.FoodCategoryResponse;
import com.countin.countin_backend.meal.api.dto.response.FoodItemResponse;
import com.countin.countin_backend.meal.application.service.FoodCatalogService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spaces/{spaceId}")
@RequiredArgsConstructor
@Tag(name = "Food Catalog", description = "Global and space food library")
@SecurityRequirement(name = "bearerAuth")
public class FoodCatalogController {

    private final FoodCatalogService foodCatalogService;

    @GetMapping("/food-categories")
    public ResponseEntity<ApiResponse<List<FoodCategoryResponse>>> listCategories(@PathVariable UUID spaceId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Food categories fetched successfully", foodCatalogService.listCategories(spaceId, callerId)));
    }

    @PostMapping("/food-categories")
    public ResponseEntity<ApiResponse<FoodCategoryResponse>> createCategory(
            @PathVariable UUID spaceId, @RequestBody @Valid CreateFoodCategoryRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        FoodCategoryResponse response = foodCatalogService.createCategory(spaceId, callerId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Food category created successfully", response));
    }

    @PostMapping("/food-categories/{categoryId}/deactivate")
    public ResponseEntity<Void> deactivateCategory(
            @PathVariable UUID spaceId, @PathVariable UUID categoryId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        foodCatalogService.deactivateCategory(spaceId, categoryId, callerId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/food-items")
    public ResponseEntity<ApiResponse<List<FoodItemResponse>>> listItems(
            @PathVariable UUID spaceId, @RequestParam(required = false) UUID categoryId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Food items fetched successfully", foodCatalogService.listItems(spaceId, callerId, categoryId)));
    }

    @PostMapping("/food-items")
    public ResponseEntity<ApiResponse<FoodItemResponse>> createItem(
            @PathVariable UUID spaceId, @RequestBody @Valid CreateFoodItemRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        FoodItemResponse response = foodCatalogService.createItem(spaceId, callerId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Food item created successfully", response));
    }

    @PutMapping("/food-items/{itemId}")
    public ResponseEntity<ApiResponse<FoodItemResponse>> updateItem(
            @PathVariable UUID spaceId,
            @PathVariable UUID itemId,
            @RequestBody @Valid UpdateFoodItemRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Food item updated successfully",
                foodCatalogService.updateItem(spaceId, itemId, callerId, request)));
    }

    @PutMapping("/food-items/{itemId}/default-price")
    public ResponseEntity<ApiResponse<FoodItemResponse>> updateItemDefaultPrice(
            @PathVariable UUID spaceId,
            @PathVariable UUID itemId,
            @RequestBody @Valid UpdateFoodItemDefaultPriceRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Food item default price updated successfully",
                foodCatalogService.updateItemDefaultPrice(spaceId, itemId, callerId, request)));
    }

    @PostMapping("/food-items/{itemId}/deactivate")
    public ResponseEntity<Void> deactivateItem(@PathVariable UUID spaceId, @PathVariable UUID itemId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        foodCatalogService.deactivateItem(spaceId, itemId, callerId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/food-items/{itemId}/extra")
    public ResponseEntity<ApiResponse<FoodItemResponse>> updateItemExtra(
            @PathVariable UUID spaceId,
            @PathVariable UUID itemId,
            @RequestBody @Valid UpdateFoodItemExtraRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Food item extra flag updated successfully",
                foodCatalogService.updateItemExtra(spaceId, itemId, callerId, request)));
    }
}
