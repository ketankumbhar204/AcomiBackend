package com.amico.amico_backend.meal.api.controller;

import com.amico.amico_backend.common.security.SecurityUtils;
import com.amico.amico_backend.common.web.ApiResponse;
import com.amico.amico_backend.meal.api.dto.request.CopyDailyMenuRequest;
import com.amico.amico_backend.meal.api.dto.request.UpsertDailyMenuRequest;
import com.amico.amico_backend.meal.api.dto.response.DailyMenuResponse;
import com.amico.amico_backend.meal.application.service.DailyMenuService;
import com.amico.amico_backend.meal.domain.model.MealType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/v1/spaces/{spaceId}/daily-menus")
@RequiredArgsConstructor
@Tag(name = "Daily Menus", description = "Daily menu planning and publishing")
@SecurityRequirement(name = "bearerAuth")
public class DailyMenuController {

    private final DailyMenuService dailyMenuService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DailyMenuResponse>>> listMenus(
            @PathVariable UUID spaceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Daily menus fetched successfully", dailyMenuService.listMenus(spaceId, callerId, from, to)));
    }

    @GetMapping("/today")
    public ResponseEntity<ApiResponse<List<DailyMenuResponse>>> getToday(@PathVariable UUID spaceId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Today's menus fetched successfully", dailyMenuService.getTodayMenus(spaceId, callerId)));
    }

    @GetMapping("/{date}")
    public ResponseEntity<ApiResponse<List<DailyMenuResponse>>> getMenusByDate(
            @PathVariable UUID spaceId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Daily menus fetched successfully", dailyMenuService.getMenusByDate(spaceId, callerId, date)));
    }

    @GetMapping("/{date}/{mealType}")
    public ResponseEntity<ApiResponse<DailyMenuResponse>> getMenu(
            @PathVariable UUID spaceId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PathVariable MealType mealType) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Daily menu fetched successfully", dailyMenuService.getMenu(spaceId, callerId, date, mealType)));
    }

    @PutMapping("/{date}/{mealType}")
    public ResponseEntity<ApiResponse<DailyMenuResponse>> upsertMenu(
            @PathVariable UUID spaceId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PathVariable MealType mealType,
            @RequestBody @Valid UpsertDailyMenuRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Daily menu saved successfully",
                dailyMenuService.upsertMenu(spaceId, callerId, date, mealType, request)));
    }

    @PostMapping("/{date}/{mealType}/publish")
    public ResponseEntity<ApiResponse<DailyMenuResponse>> publishMenu(
            @PathVariable UUID spaceId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PathVariable MealType mealType) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Daily menu published successfully",
                dailyMenuService.publishMenu(spaceId, callerId, date, mealType)));
    }

    @PostMapping("/{targetDate}/{mealType}/copy-from/{sourceDate}")
    public ResponseEntity<ApiResponse<DailyMenuResponse>> copyMenu(
            @PathVariable UUID spaceId,
            @PathVariable("targetDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate,
            @PathVariable MealType mealType,
            @PathVariable("sourceDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate sourceDate,
            @RequestBody(required = false) CopyDailyMenuRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Daily menu copied successfully",
                dailyMenuService.copyMenu(spaceId, callerId, targetDate, mealType, sourceDate, request)));
    }

    @DeleteMapping("/{date}/{mealType}")
    public ResponseEntity<Void> deleteMenu(
            @PathVariable UUID spaceId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PathVariable MealType mealType) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        dailyMenuService.deleteDraftMenu(spaceId, callerId, date, mealType);
        return ResponseEntity.noContent().build();
    }
}
