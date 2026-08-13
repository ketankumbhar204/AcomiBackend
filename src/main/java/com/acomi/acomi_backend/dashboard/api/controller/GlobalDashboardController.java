package com.acomi.acomi_backend.dashboard.api.controller;

import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.dashboard.api.dto.response.GlobalDashboardResponse;
import com.acomi.acomi_backend.dashboard.application.service.GlobalDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Global Dashboard", description = "Cross-space owner overview for My Spaces")
@SecurityRequirement(name = "bearerAuth")
public class GlobalDashboardController {

    private final GlobalDashboardService globalDashboardService;

    @GetMapping("/global")
    @Operation(
            summary = "Get global owner dashboard",
            description = "Aggregates attention-required actions and recent activity across spaces "
                    + "where the caller is OWNER or MANAGER. Uses notification / pending-action rows "
                    + "as the single source of truth.")
    public ResponseEntity<ApiResponse<GlobalDashboardResponse>> getGlobalDashboard(
            @RequestParam(required = false) String month,
            @RequestParam(required = false, defaultValue = "true") boolean sync) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        GlobalDashboardResponse response =
                globalDashboardService.getGlobalDashboard(callerId, month, sync);
        return ResponseEntity.ok(ApiResponse.success("Global dashboard fetched successfully", response));
    }
}
