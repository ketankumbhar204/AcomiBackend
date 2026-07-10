package com.countin.countin_backend.notification.api.controller;

import com.countin.countin_backend.common.security.SecurityUtils;
import com.countin.countin_backend.common.web.ApiResponse;
import com.countin.countin_backend.dashboard.application.service.DashboardAccessService;
import com.countin.countin_backend.meal.application.service.MealAccessService;
import com.countin.countin_backend.notification.api.dto.response.NotificationListResponse;
import com.countin.countin_backend.notification.api.dto.response.NotificationResponse;
import com.countin.countin_backend.notification.api.dto.response.PendingActionsSummaryResponse;
import com.countin.countin_backend.notification.application.service.NotificationService;
import com.countin.countin_backend.notification.application.service.PendingActionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spaces/{spaceId}")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notifications and pending actions")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;
    private final PendingActionService pendingActionService;
    private final DashboardAccessService dashboardAccessService;
    private final MealAccessService mealAccessService;

    @GetMapping("/pending-actions")
    @Operation(
            summary = "Get pending actions",
            description = "Returns unresolved actionable notifications for the current member. "
                    + "Payment actions are synced from the payment store before aggregation.")
    public ResponseEntity<ApiResponse<PendingActionsSummaryResponse>> getPendingActions(
            @PathVariable UUID spaceId, @RequestParam(required = false) String month) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        dashboardAccessService.requireActiveMembership(spaceId, callerId);
        PendingActionsSummaryResponse response =
                pendingActionService.getPendingActions(spaceId, callerId, month);
        return ResponseEntity.ok(ApiResponse.success("Pending actions fetched successfully", response));
    }

    @GetMapping("/notifications")
    @Operation(summary = "List in-app notifications for the current user")
    public ResponseEntity<ApiResponse<NotificationListResponse>> listNotifications(
            @PathVariable UUID spaceId,
            @RequestParam(required = false, defaultValue = "false") boolean actionableOnly) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        var membership = dashboardAccessService.requireActiveMembership(spaceId, callerId);
        // Do NOT call getPendingActions here — that sync is expensive and already runs via
        // dashboard-summary / pending-actions. Only clear leaked owner-only rows for tenants.
        if (!mealAccessService.canManageMeals(membership)) {
            pendingActionService.clearOwnerOnlyActionsForUser(spaceId, callerId);
        }
        return ResponseEntity.ok(ApiResponse.success(
                "Notifications fetched successfully",
                notificationService.listForUser(spaceId, callerId, actionableOnly)));
    }

    @PostMapping("/notifications/{notificationId}/read")
    @Operation(summary = "Mark a notification as read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(
            @PathVariable UUID spaceId, @PathVariable UUID notificationId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        dashboardAccessService.requireActiveMembership(spaceId, callerId);
        return ResponseEntity.ok(ApiResponse.success(
                "Notification marked as read",
                notificationService.markRead(spaceId, notificationId, callerId)));
    }

    @PostMapping("/notifications/{notificationId}/resolve")
    @Operation(summary = "Resolve an actionable notification")
    public ResponseEntity<ApiResponse<NotificationResponse>> resolve(
            @PathVariable UUID spaceId, @PathVariable UUID notificationId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        dashboardAccessService.requireActiveMembership(spaceId, callerId);
        return ResponseEntity.ok(ApiResponse.success(
                "Notification resolved",
                notificationService.resolve(spaceId, notificationId, callerId)));
    }
}
