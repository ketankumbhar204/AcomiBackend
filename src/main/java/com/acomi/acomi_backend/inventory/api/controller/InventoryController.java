package com.acomi.acomi_backend.inventory.api.controller;

import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.inventory.api.dto.request.CreateInventoryCategoryRequest;
import com.acomi.acomi_backend.inventory.api.dto.request.CreateInventoryItemRequest;
import com.acomi.acomi_backend.inventory.api.dto.request.CreateInventorySupplierRequest;
import com.acomi.acomi_backend.inventory.api.dto.request.InventoryStockMoveRequest;
import com.acomi.acomi_backend.inventory.api.dto.request.UpdateInventoryItemRequest;
import com.acomi.acomi_backend.inventory.api.dto.response.InventoryCategoryResponse;
import com.acomi.acomi_backend.inventory.api.dto.response.InventoryDashboardResponse;
import com.acomi.acomi_backend.inventory.api.dto.response.InventoryItemResponse;
import com.acomi.acomi_backend.inventory.api.dto.response.InventorySupplierResponse;
import com.acomi.acomi_backend.inventory.api.dto.response.InventoryTransactionResponse;
import com.acomi.acomi_backend.inventory.application.service.InventoryService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spaces/{spaceId}/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Space-scoped stock and asset inventory")
@SecurityRequirement(name = "bearerAuth")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<InventoryDashboardResponse>> getDashboard(@PathVariable UUID spaceId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Inventory dashboard fetched successfully",
                inventoryService.getDashboard(spaceId, callerId)));
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<InventoryCategoryResponse>>> listCategories(
            @PathVariable UUID spaceId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Inventory categories fetched successfully",
                inventoryService.listCategories(spaceId, callerId)));
    }

    @PostMapping("/categories")
    public ResponseEntity<ApiResponse<InventoryCategoryResponse>> createCategory(
            @PathVariable UUID spaceId, @RequestBody @Valid CreateInventoryCategoryRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Inventory category created successfully",
                        inventoryService.createCategory(spaceId, callerId, request)));
    }

    @DeleteMapping("/categories/{categoryId}")
    public ResponseEntity<Void> deleteCategory(
            @PathVariable UUID spaceId, @PathVariable UUID categoryId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        inventoryService.deleteCategory(spaceId, categoryId, callerId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/items")
    public ResponseEntity<ApiResponse<List<InventoryItemResponse>>> listItems(
            @PathVariable UUID spaceId, @RequestParam(required = false) UUID categoryId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Inventory items fetched successfully",
                inventoryService.listItems(spaceId, callerId, categoryId)));
    }

    @GetMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> getItem(
            @PathVariable UUID spaceId, @PathVariable UUID itemId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Inventory item fetched successfully",
                inventoryService.getItem(spaceId, itemId, callerId)));
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> createItem(
            @PathVariable UUID spaceId, @RequestBody @Valid CreateInventoryItemRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Inventory item created successfully",
                        inventoryService.createItem(spaceId, callerId, request)));
    }

    @PutMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> updateItem(
            @PathVariable UUID spaceId,
            @PathVariable UUID itemId,
            @RequestBody @Valid UpdateInventoryItemRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Inventory item updated successfully",
                inventoryService.updateItem(spaceId, itemId, callerId, request)));
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Void> deleteItem(@PathVariable UUID spaceId, @PathVariable UUID itemId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        inventoryService.deleteItem(spaceId, itemId, callerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/items/{itemId}/stock-moves")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> stockMove(
            @PathVariable UUID spaceId,
            @PathVariable UUID itemId,
            @RequestBody @Valid InventoryStockMoveRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Stock updated successfully",
                inventoryService.stockMove(spaceId, itemId, callerId, request)));
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<List<InventoryTransactionResponse>>> listTransactions(
            @PathVariable UUID spaceId, @RequestParam(required = false) UUID itemId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Inventory transactions fetched successfully",
                inventoryService.listTransactions(spaceId, callerId, itemId)));
    }

    @GetMapping("/suppliers")
    public ResponseEntity<ApiResponse<List<InventorySupplierResponse>>> listSuppliers(
            @PathVariable UUID spaceId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Inventory suppliers fetched successfully",
                inventoryService.listSuppliers(spaceId, callerId)));
    }

    @PostMapping("/suppliers")
    public ResponseEntity<ApiResponse<InventorySupplierResponse>> createSupplier(
            @PathVariable UUID spaceId, @RequestBody @Valid CreateInventorySupplierRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Inventory supplier created successfully",
                        inventoryService.createSupplier(spaceId, callerId, request)));
    }
}
