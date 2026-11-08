package com.acomi.acomi_backend.admin.api.controller;

import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.payment.application.service.MonthlyBillingMonitorService;
import com.acomi.acomi_backend.payment.application.service.MonthlyBillingMonitorService.MonitorSnapshot;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/monthly-billing")
@RequiredArgsConstructor
@Tag(name = "Admin Monthly Billing")
@SecurityRequirement(name = "bearerAuth")
public class AdminMonthlyBillingController {

    private final MonthlyBillingMonitorService monitorService;

    @GetMapping
    @Operation(summary = "Monthly rent generation and customer notification status")
    public ResponseEntity<ApiResponse<MonitorSnapshot>> snapshot() {
        return ResponseEntity.ok(ApiResponse.success(monitorService.snapshot()));
    }

    @PostMapping("/run")
    @Operation(summary = "Run due monthly billing now. Existing obligations are not duplicated.")
    public ResponseEntity<ApiResponse<MonitorSnapshot>> run() {
        return ResponseEntity.ok(ApiResponse.success(
                "Monthly billing run finished", monitorService.runNow()));
    }

    @PostMapping("/notifications/retry-failed")
    @Operation(summary = "Retry failed customer notifications. Does not create payments.")
    public ResponseEntity<ApiResponse<MonitorSnapshot>> retryFailed() {
        return ResponseEntity.ok(ApiResponse.success(
                "Failed notifications queued again", monitorService.retryFailedNotifications()));
    }
}
