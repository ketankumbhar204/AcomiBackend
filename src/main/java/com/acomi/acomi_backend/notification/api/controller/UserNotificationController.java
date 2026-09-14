package com.acomi.acomi_backend.notification.api.controller;

import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.notification.api.dto.response.UserNotificationListResponse;
import com.acomi.acomi_backend.notification.api.dto.response.UserNotificationResponse;
import com.acomi.acomi_backend.notification.application.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "User Notifications", description = "Authenticated requester notification inbox. Never returns owner contact.")
@SecurityRequirement(name = "bearerAuth")
public class UserNotificationController {

    private final NotificationService notificationService;

    @GetMapping("/me")
    @Operation(summary = "List my requester notifications")
    public ResponseEntity<ApiResponse<UserNotificationListResponse>> listMine(
            @PageableDefault(size = 20) Pageable pageable) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Notifications fetched successfully", notificationService.listForCurrentUser(callerId, pageable)));
    }

    @PostMapping("/{notificationId}/read")
    @Operation(summary = "Mark one of my requester notifications as read")
    public ResponseEntity<ApiResponse<UserNotificationResponse>> markRead(@PathVariable UUID notificationId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Notification marked as read",
                notificationService.markReadForCurrentUser(notificationId, callerId)));
    }
}
