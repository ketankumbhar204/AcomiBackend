package com.acomi.acomi_backend.admin.api.controller;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/notifications")
@RequiredArgsConstructor
@Tag(name = "Admin Notifications")
@SecurityRequirement(name = "bearerAuth")
public class AdminNotificationController {

    private final NotificationService notificationService;

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
}
