package com.amico.amico_backend.dashboard.api.controller;

import com.amico.amico_backend.common.security.SecurityUtils;
import com.amico.amico_backend.common.web.ApiResponse;
import com.amico.amico_backend.dashboard.api.dto.response.DashboardSummaryResponse;
import com.amico.amico_backend.dashboard.application.service.DashboardAccessService;
import com.amico.amico_backend.dashboard.application.service.SpaceDashboardSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spaces/{spaceId}")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Space dashboard summary APIs")
@SecurityRequirement(name = "bearerAuth")
public class SpaceDashboardController {

    private final DashboardAccessService dashboardAccessService;
    private final SpaceDashboardSummaryService spaceDashboardSummaryService;

    @GetMapping("/dashboard-summary")
    @Operation(
            summary = "Get space dashboard summary",
            description = "Returns unified financial cards, operational metrics, and attention items "
                    + "for the current month. Financial expected charges come from meal selections "
                    + "and/or occupancy contracts — never from published menus alone.")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> getDashboardSummary(
            @PathVariable UUID spaceId,
            @RequestParam(required = false) String month) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        dashboardAccessService.requireViewDashboard(spaceId, callerId);
        DashboardSummaryResponse response =
                spaceDashboardSummaryService.getSummary(spaceId, callerId, month);
        return ResponseEntity.ok(ApiResponse.success("Dashboard summary fetched successfully", response));
    }
}
