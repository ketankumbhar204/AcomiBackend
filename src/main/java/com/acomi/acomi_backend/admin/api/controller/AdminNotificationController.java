package com.acomi.acomi_backend.admin.api.controller;

import com.acomi.acomi_backend.admin.api.dto.request.AdminNotificationTestRequest;
import com.acomi.acomi_backend.admin.api.dto.response.AdminNotificationTestPageResponse;
import com.acomi.acomi_backend.admin.api.dto.response.AdminNotificationTestPageResponse.AttemptItem;
import com.acomi.acomi_backend.admin.application.service.AdminNotificationTestService;
import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.notification.api.dto.response.NotificationListResponse;
import com.acomi.acomi_backend.notification.api.dto.response.NotificationResponse;
import com.acomi.acomi_backend.notification.application.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import com.acomi.acomi_backend.admin.api.dto.response.AdminNotificationTestDeleteResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/notifications")
@RequiredArgsConstructor
@Tag(name = "Admin Notifications")
@SecurityRequirement(name = "bearerAuth")
public class AdminNotificationController {

    private final NotificationService notificationService;
    private final AdminNotificationTestService notificationTestService;

    @GetMapping
    @Operation(summary = "List in-app notifications for the signed-in Admin")
    public ResponseEntity<ApiResponse<NotificationListResponse>> list() {
        UUID adminId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(notificationService.listForAdminUser(adminId)));
    }

    @PostMapping("/{notificationId}/read")
    @Operation(summary = "Mark an Admin notification as read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(@PathVariable UUID notificationId) {
        UUID adminId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Notification marked as read", notificationService.markReadForAdmin(notificationId, adminId)));
    }

    @GetMapping("/test")
    @Operation(summary = "Admin notification testing catalog, own registrations, and recent results")
    public ResponseEntity<ApiResponse<AdminNotificationTestPageResponse>> testPage() {
        UUID adminId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(notificationTestService.page(adminId)));
    }

    @PostMapping("/test")
    @Operation(summary = "Send one test notification to the signed-in admin's own devices")
    public ResponseEntity<ApiResponse<AttemptItem>> sendTest(
            @Valid @RequestBody AdminNotificationTestRequest request) {
        UUID adminId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Notification test finished", notificationTestService.send(adminId, request)));
    }

    @DeleteMapping("/test/{attemptId}")
    @Operation(summary = "Delete one notification-test history row owned by the signed-in admin")
    public ResponseEntity<ApiResponse<AdminNotificationTestDeleteResponse>> deleteTest(
            @PathVariable UUID attemptId) {
        UUID adminId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(notificationTestService.deleteOne(adminId, attemptId)));
    }

    @DeleteMapping("/test")
    @Operation(summary = "Delete notification-test history for the signed-in admin")
    public ResponseEntity<ApiResponse<AdminNotificationTestDeleteResponse>> deleteAllTests() {
        UUID adminId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(notificationTestService.deleteAll(adminId)));
    }
}
