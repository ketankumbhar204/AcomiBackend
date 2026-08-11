package com.amico.amico_backend.meal.api.controller;

import com.amico.amico_backend.common.security.SecurityUtils;
import com.amico.amico_backend.common.web.ApiResponse;
import com.amico.amico_backend.meal.api.dto.response.MenuHistoryPageResponse;
import com.amico.amico_backend.meal.application.service.MenuPlanningHistoryService;
import com.amico.amico_backend.meal.domain.model.MealType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spaces/{spaceId}/menu-history")
@RequiredArgsConstructor
@Tag(name = "Menu History", description = "Meal-specific planning history for the menu planner")
@SecurityRequirement(name = "bearerAuth")
public class MenuPlanningHistoryController {

    private final MenuPlanningHistoryService menuPlanningHistoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<MenuHistoryPageResponse>> listHistory(
            @PathVariable UUID spaceId,
            @RequestParam MealType mealType,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int limit) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Menu history fetched successfully",
                menuPlanningHistoryService.listHistory(spaceId, callerId, mealType, search, page, limit)));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> clearHistory(
            @PathVariable UUID spaceId, @RequestParam MealType mealType) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        menuPlanningHistoryService.clearHistory(spaceId, callerId, mealType);
        return ResponseEntity.ok(ApiResponse.success("Menu history cleared successfully", null));
    }

    /**
     * Rebuild history for one meal type from that meal's daily menus only
     * (Breakfast menus → Breakfast history, etc.).
     */
    @PostMapping("/rebuild")
    public ResponseEntity<ApiResponse<Void>> rebuildHistory(
            @PathVariable UUID spaceId, @RequestParam MealType mealType) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        menuPlanningHistoryService.rebuildForMeal(spaceId, callerId, mealType);
        return ResponseEntity.ok(ApiResponse.success("Menu history rebuilt successfully", null));
    }
}
